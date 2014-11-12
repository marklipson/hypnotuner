package com.marklipson.musicgen;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON parser/formatter.
 * 
 * To read JSON: JSON value = JSON.parse(
 * "{ id: 105, person: {name:'Joe",age:33}, message: 'hello' }" );
 * value.getString( "message" ); // 'hello' value.getInt( "person.name" ); //
 * 'Joe'
 * 
 * To write JSON: JSON value ... value.toString();
 * 
 * To construct JSON: JSON value = JSON.createObject(); value.setString(
 * "message", "hello" );
 * 
 * Marshaling:
 * 
 * MyObject myObject = ...; JSON value = JSON.objectToJSON( myObject );
 */
public class JSON implements Cloneable, Iterable<JSON>
{
  static final private Object NULL = new Object();
  static final private Object REMOVE = new Object();

  static private class Parser
  {
    char[] input;
    int pos;
    boolean strict;
    private static final String literalDelim = "{}[],:'\" \t\n\r";

    Parser(final String in)
    {
      input = in.toCharArray();
      pos = 0;
    }

    private char cur()
    {
      if (pos < input.length)
        return input[pos];
      else
        return 0;
    }

    private boolean eof()
    {
      return pos >= input.length;
    }

    private void next()
    {
      if (pos < input.length)
        pos++;
    }

    private void ws()
    {
      while (!eof())
      {
        if (Character.isWhitespace(cur()))
          next();
        else if (cur() == '/' && pos + 1 < input.length && input[pos + 1] == '/')
        {
          while (!eof() && cur() != '\n')
            next();
          continue;
        }
        else if (cur() == '/' && pos + 1 < input.length && input[pos + 1] == '*')
        {
          while (!eof())
          {
            if (cur() == '*' && pos + 1 < input.length && input[pos + 1] == '/')
            {
              pos += 2;
              break;
            }
            else
              next();
          }
          continue;
        }
        else
          break;
      }
    }

    private int hexDigit( final int in )
    {
      final char ch = cur();
      if (ch >= '0' && ch <= '9')
      {
        next();
        return in * 16 + (ch - '0');
      }
      if (ch >= 'a' && ch <= 'f')
      {
        next();
        return in * 16 + (ch - 'a') + 10;
      }
      if (ch >= 'A' && ch <= 'F')
      {
        next();
        return in * 16 + (ch - 'A') + 10;
      }
      return in;
    }

    /**
     * An identifier, quoted value, etc.
     */
    protected Object literal()
    {
      ws();
      if (eof())
        return null;
      final char ch = cur();
      // quoted string
      if (ch == '"' || ch == '\'')
      {
        final char delim = ch;
        next();
        final StringBuilder out = new StringBuilder();
        while (!eof())
        {
          char ch2 = cur();
          next();
          if (ch2 == '\\')
          {
            ch2 = cur();
            next();
            if (ch2 == 'n')
              ch2 = '\n';
            else if (ch2 == 't')
              ch2 = '\t';
            else if (ch2 == 'b')
              ch2 = '\b';
            else if (ch2 == 'f')
              ch2 = '\f';
            else if (ch2 == 'r')
              ch2 = '\r';
            else if (ch2 == 'u')
            {
              // 4-digit unicode
              int c = 0;
              c = hexDigit(c);
              c = hexDigit(c);
              c = hexDigit(c);
              c = hexDigit(c);
              ch2 = (char) c;
            }
            out.append(ch2);
          }
          else if (ch2 == delim)
            break;
          else
            out.append(ch2);
        }
        return out.toString();
      }
      // number, identifier, etc.
      else if (literalDelim.indexOf(cur()) == -1)
      {
        final int p0 = pos;
        final StringBuilder out = new StringBuilder();
        while (!eof() && literalDelim.indexOf(cur()) == -1)
        {
          out.append(cur());
          next();
        }
        final String outStr = out.toString();
        if (outStr.equals("true") || outStr.equals("false"))
          return parseBoolean(outStr, false);
        if (outStr.equals("null"))
          return NULL;
        if (outStr.matches("\\-?\\d+"))
        {
          final long v = Long.parseLong(outStr);
          if (Math.abs(v) < Integer.MAX_VALUE)
            return (int) v;
          return v;
        }
        if (outStr.matches("\\-?\\d+(\\.\\d+)?([eE][\\-\\+]?\\d+)?"))
          return Double.parseDouble(outStr);
        // in 'strict' mode, ignore literals that are not understood (stop
        // parsing here)
        if (strict)
        {
          pos = p0;
          return null;
        }
        return outStr;
      }
      else
        return null;
    }

    /**
     * An object.
     */
    protected JSON object()
    {
      ws();
      if (cur() != '{')
        return null;
      next();
      final JSON out = JSON.createObject();
      for (;;)
      {
        final Object name = literal();
        if (name == null)
          break;
        ws();
        Object value = "";
        if (cur() == ':')
        {
          next();
          value = value();
        }
        if (value != null)
          out.set(name.toString(), value, false);
        ws();
        if (cur() == ',')
        {
          next();
          continue;
        }
        break;
      }
      ws();
      if (cur() == '}')
        next();
      return out;
    }

    /**
     * An array.
     */
    protected JSON array()
    {
      ws();
      if (cur() != '[')
        return null;
      next();
      final JSON out = JSON.createArray();
      int length = 0;
      for (;;)
      {
        final Object value = value();
        if (value == null)
          break;
        out.set(String.valueOf(length), value, false);
        length++;
        ws();
        if (cur() == ',')
        {
          next();
          continue;
        }
        ws();
        break;
      }
      if (cur() == ']')
        next();
      return out;
    }

    protected Object value()
    {
      Object out;
      out = object();
      if (out != null)
        return out;
      out = array();
      if (out != null)
        return out;
      out = literal();
      if (out != null)
        return out;
      return null;
    }
  }

  private Map<String, Object> fields;
  private boolean isArray;
  private boolean isSimple;

  private JSON()
  {
  }

  /**
   * Create a new, blank object. Populate with set*(String,...) methods.
   */
  public static JSON createObject()
  {
    final JSON json = new JSON();
    json.fields = new LinkedHashMap<String, Object>();
    json.fields = Collections.synchronizedMap(json.fields);
    return json;
  }

  /**
   * Create a new, empty array. Populate with set*(int,...) methods.
   */
  public static JSON createArray()
  {
    final JSON json = new JSON();
    json.fields = new HashMap<String, Object>();
    json.fields = Collections.synchronizedMap(json.fields);
    json.isArray = true;
    return json;
  }

  /**
   * Merge values from another object.
   */
  public void merge( final JSON other )
  {
    if (other == null)
      return;
    if (_isMarker(other, _same))
      return;
    if (other.isSimple)
    {
      fields.clear();
      fields.putAll(other.fields);
      isSimple = true;
      isArray = false;
      return;
    }
    if (other.isArray)
    {
      int newSize = other.getArraySize();
      for (int n = 0; n < newSize; n++)
      {
        final String key = String.valueOf(n);
        final Object oldSub = fields.get(key);
        final Object newSub = other.fields.get(key);
        if (!_isMarker(newSub, _same))
        {
          if (_isMarker(newSub, _remove))
          {
            removeArrayElement(n--);
            newSize--;
          }
          else if (oldSub instanceof JSON)
            ((JSON) oldSub).merge((newSub instanceof JSON) ? (JSON) newSub : createSimple(newSub));
          else
            set(n, newSub);
        }
      }
      isSimple = false;
      return;
    }
    for (final String key : other.getFields())
    {
      final Object newValue = other.fields.get(key);
      if (!_isMarker(newValue, _same))
      {
        if (_isMarker(newValue, _remove))
          fields.remove(key);
        else if (!fields.containsKey(key))
          // if key is new, simply add it in
          fields.put(key, newValue);
        else
        {
          // key already exists
          final Object v1 = fields.get(key);
          final Object v2 = newValue;
          if (v1 instanceof JSON)
            // recurse to merge complex values
            ((JSON) v1).merge((v2 instanceof JSON) ? (JSON) v2 : createSimple(v2));
          else
            // overwrite simple values
            fields.put(key, v2);
        }
      }
    }
  }

  /**
   * JSON can be used to hold simple (primitive) values on their own, not in the
   * context of an object or an array.
   */
  public static JSON createSimple( final Object simple )
  {
    final JSON json = new JSON();
    json.set("", simple);
    json.isSimple = true;
    return json;
  }

  /**
   * Store a new field.
   * 
   * @param parse
   *          If true (the default), the name will be split on dots/periods, and
   *          values dissemminated into the object structure. To store keys
   *          (names) that contain dots, set this parameter to false.
   */
  public void set( final String name, final Object value )
  {
    set(name, value, true);
  }

  public void set( String name, Object value, final boolean parse )
  {
    if (name == null)
      return;
    if (value == null)
      value = NULL;
    if (value instanceof Double)
    {
      final Double v = (Double) value;
      if (v.isNaN() || v.isInfinite())
        value = NULL;
    }
    if (value instanceof Float)
    {
      final Float v = (Float) value;
      if (v.isNaN() || v.isInfinite())
        value = NULL;
    }
    if (fields == null)
    {
      fields = new LinkedHashMap<String, Object>();
      fields = Collections.synchronizedMap(fields);
    }
    if (parse && name.indexOf('.') != -1)
    {
      // check for fielded request
      final String[] parts = fastSplit(name, '.');
      if (parts.length > 1)
      {
        JSON next = getComplex(parts[0]);
        if (next == null)
        {
          String nextPart = parts[1];
          final int pD = nextPart.indexOf('.');
          if (pD != -1)
            nextPart = nextPart.substring(0, pD);
          final boolean array = testArrayIndex(nextPart);
          set(parts[0], next = array ? JSON.createArray() : JSON.createObject());
        }
        next.set(parts[1], value);
        return;
      }
    }
    if (value == REMOVE)
    {
      if (isArray() && testArrayIndex(name))
        removeArrayElement(Integer.parseInt(cleanArrayIndex(name)));
      else
        fields.remove(name);
    }
    else
    {
      if (value instanceof JSON && ((JSON) value).isSimple())
        value = ((JSON) value).getSimpleValue_Object();
      else if (value.getClass().isArray())
      {
        // clone arrays before inserting
        final int len = Array.getLength(value);
        final JSON arr = JSON.createArray();
        for (int nA = 0; nA < len; nA++)
        {
          final Object v = Array.get(value, nA);
          arr.set(nA, v);
        }
        value = arr;
      }
      // inserting into an array? adjust array for insertion
      if (isArray() && testArrayIndex(name))
      {
        // make array index a clean integer-string
        name = cleanArrayIndex(name);
        // fill in missing values prior to index
        final int nIndex = Integer.parseInt(name);
        for (int n = getArraySize(); n < nIndex; n++)
        {
          final String key = String.valueOf(n);
          if (!fields.containsKey(key))
            fields.put(key, NULL);
        }
      }
      fields.put(name, value);
    }
  }

  /**
   * Store a value in an array.
   */
  public void set( final int index, Object value )
  {
    // FIXME
    if (value == null)
      value = NULL;
    set(String.valueOf(index), value);
  }

  public void remove( final Collection<String> names, final boolean deep )
  {
    if (names == null || fields == null)
      return;

    for (final String name : names)
      remove(name);

    if (!deep)
      return;

    for (final Object o : fields.values())
    {
      if (o instanceof JSON)
        ((JSON) o).remove(names, deep);
    }
  }

  /**
   * Remove an element.
   */
  public void remove( final String name )
  {
    set(name, REMOVE);
  }

  /**
   * Arrays are indexed by number ("0", etc..)
   */
  public boolean isArray()
  {
    return isArray;
  }

  /**
   * A non-null object.
   */
  public boolean isObject()
  {
    return !isArray && !isSimple && fields != null;
  }

  /**
   * An empty object or array.
   */
  public boolean isEmpty()
  {
    return fields == null || fields.size() == 0;
  }

  /**
   * Standalone value is stored in this mode.
   */
  public boolean isSimple()
  {
    return isSimple;
  }

  public String getSimpleValue()
  {
    if (isSimple())
    {
      if (fields == null)
        return null;
      final Object v = fields.get("");
      if (v == null || v == NULL)
        return null;
      return v.toString();
    }
    return null;
  }

  public Object getSimpleValue_Object()
  {
    if (isSimple())
    {
      if (fields == null)
        return null;
      final Object v = fields.get("");
      if (v == null || v == NULL)
        return null;
      return v;
    }
    return null;
  }

  /**
   * Get all field names.
   */
  public String[] getFields()
  {
    if (fields == null)
      return new String[0];
    return fields.keySet().toArray(new String[fields.size()]);
  }

  /**
   * Get array size.
   */
  public int getArraySize()
  {
    if (isArray)
    {
      if (fields == null)
        return 0;
      return fields.size();
    }
    else
      return 0;
  }

  /**
   * Fetch an array.
   */
  public String[] getArray( final String name )
  {
    final JSON arr = (name == null) ? this : getComplex(name);
    if (arr == null)
      return null;
    if (arr.isArray())
    {
      final String[] out = new String[arr.getArraySize()];
      for (int n = 0; n < out.length; n++)
        out[n] = arr.getString(n);
      return out;
    }
    if (arr.isSimple())
      return new String[]
      { arr.getSimpleValue() };
    return null;
  }

  /**
   * Remove array element.
   */
  public JSON removeArrayElement( final int index )
  {
    if (index < 0)
      return null;
    final int size = getArraySize();
    if (index >= size)
      return null;
    final JSON elem = getComplex(index);
    for (int n = index; n < size - 1; n++)
      fields.put(String.valueOf(n), fields.get(String.valueOf(n + 1)));
    fields.remove(String.valueOf(size - 1));
    return elem;
  }

  /**
   * Add array element.
   */
  public void addArrayElement( Object newValue )
  {
    // FIXME
    if (newValue == null)
      newValue = NULL;
    if (newValue != null)
      set(getArraySize(), newValue);
  }

  /**
   * Get a value as a generic object.
   */
  public Object get( final String field )
  {
    return get(field, true);
  }

  public Object get( final String field, final boolean parse )
  {
    if (field == null || fields == null)
      return null;
    if (parse && field.indexOf('.') != -1)
    {
      final JSON v = getComplex(field, parse);
      if (v == null)
        return null;
      if (v.isSimple)
        return v.getSimpleValue_Object();
      return v;
    }
    else
    {
      Object out = fields.get(field);
      if (out == NULL)
        out = null;
      return out;
    }
  }

  /**
   * Get a value as a string. "field" can contain a deeply nested request,
   * separated by periods.
   */
  public String getString( final int index )
  {
    return getString(String.valueOf(index), null, true);
  }

  public String getString( final String field )
  {
    return getString(field, null, true);
  }

  public String getString( final String field, final boolean parse )
  {
    return getString(field, null, parse);
  }

  public String getString( final String field, final String defaultValue )
  {
    return getString(field, defaultValue, true);
  }

  public String getString( final String field, final String defaultValue, final boolean parse )
  {
    final JSON v = getComplex(field, parse);
    if (v == null)
      return defaultValue;
    if (v.isSimple)
      return v.getSimpleValue();
    return v.toString();
    // return isArray ? "[array]" : "[object]";
  }

  /**
   * Get an integer.
   */
  public int getInt( final String field, final int defaultValue )
  {
    final String s = getString(field);
    if (s == null || !s.matches("-?\\d+"))
      return defaultValue;
    return Integer.parseInt(s);
  }

  public Integer getInt( final String field )
  {
    final String s = getString(field);
    if (s == null || !s.matches("-?\\d+"))
      return null;
    return Integer.parseInt(s);
  }

  /**
   * Get a boolean value.
   */
  public boolean getBoolean( final String field, final boolean defaultValue )
  {
    final String s = getString(field);
    return parseBoolean(s, defaultValue);
  }

  /**
   * Get a long.
   */
  public long getLong( final String field, final long defaultValue )
  {
    final Long foundValue = getLong(field);
    return foundValue != null ? foundValue : defaultValue;
  }

  /**
   * Get a long.
   */
  public Long getLong( final String field )
  {
    final String value = getString(field);
    if (value != null && value.matches("-?\\d+"))
    {
      return Long.parseLong(value);
    }
    else
    {
      return null;
    }
  }

  /**
   * Get a double.
   */
  public double getDouble( final String field, final double defaultValue )
  {
    final String s = getString(field);
    if (s == null || !s.matches("-?\\d+(\\.\\d*)?"))
      return defaultValue;
    return Double.parseDouble(s);
  }

  /**
   * Get a complex value. "field" can contain a deeply nested request, separated
   * by periods.
   */
  public JSON getComplex( final int index )
  {
    return getComplex(String.valueOf(index));
  }

  public JSON getComplex( final String field )
  {
    return getComplex(field, true);
  }

  public JSON getComplex( final String field, final boolean parse )
  {
    // check for fielded request
    if (parse)
    {
      final String[] parts = fastSplit(field, '.');
      if (parts.length > 1)
      {
        final JSON next = getComplex(parts[0]);
        if (next == null)
          return null;
        return next.getComplex(parts[1]);
      }
    }
    // get requested field
    if (fields == null)
      return null;
    final Object v = fields.get(field);
    if (v == null)
      return null;
    if (v instanceof JSON)
      return (JSON) v;
    return createSimple(v);
  }

  static public JSON parse( final String in )
  {
    return parse(in, false);
  }

  static public JSON parse( final String in, final boolean strict )
  {
    if (in == null)
      return null;
    final Parser p = new Parser(in);
    p.strict = strict;
    final Object out = p.value();
    if (out == null)
      return null;
    if (out instanceof JSON)
      return (JSON) out;
    // if a simple value is returned, wrap it in a JSON with a blank key
    return createSimple(out);
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((fields == null) ? 0 : fields.hashCode());
    result = prime * result + (isArray ? 1231 : 1237);
    result = prime * result + (isSimple ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals( Object obj )
  {
    if (this == obj)
      return true;
    if (obj == null)
      return false;

    if (getClass() != obj.getClass())
    {
      try
      {
        obj = objectToJSON(obj);
      }
      catch (final Exception x)
      {
        return false;
      }
    }

    final JSON other = (JSON) obj;
    if (fields == null)
    {
      if (other.fields != null)
        return false;
    }
    else if (!fields.equals(other.fields))
      return false;
    if (isArray != other.isArray)
      return false;
    if (isSimple != other.isSimple)
      return false;
    return true;

  }

  /**
   * Diff two values.
   */
  static public JSON diff( final JSON jOld, final JSON jNew )
  {
    return diff(jOld, jNew, false);
  }

  static private JSON diff( final JSON jOld, final JSON jNew, final boolean inArray )
  {
    if (jOld == null)
      return jNew;
    if (jNew == null)
      return _remove;
    if (jNew.isSimple())
    {
      if (jOld.equals(jNew))
        return inArray ? _same : null;
      return jNew;
    }
    if (jNew.isArray())
    {
      if (!jOld.isArray())
        return jNew;
      if (jOld.equals(jNew))
        return inArray ? _same : null;
      final JSON out = JSON.createArray();
      int n = 0;
      for (; n < jOld.getArraySize() && n < jNew.getArraySize(); n++)
      {
        final JSON subOld = jOld.getComplex(n);
        final JSON subNew = jNew.getComplex(n);
        out.set(n, diff(subOld, subNew, true));
      }
      for (; n < jNew.getArraySize(); n++)
        out.set(n, jNew.getComplex(n));
      for (; n < jOld.getArraySize(); n++)
        out.set(n, _remove);
      return out;
    }
    if (jOld.equals(jNew))
      return inArray ? _same : null;
    final JSON out = JSON.createObject();
    for (final String key : jNew.getFields())
    {
      final JSON subOld = jOld.getComplex(key);
      final JSON subNew = jNew.getComplex(key);
      final JSON subDiff = diff(subOld, subNew);
      if (subDiff != null)
        out.set(key, subDiff);
    }
    for (final String key : jOld.getFields())
    {
      final JSON subNew = jNew.getComplex(key);
      if (subNew == null)
        out.set(key, _remove);
    }
    return out;
  }

  static final JSON _same = JSON.parse("{$same:1}");
  static final JSON _remove = JSON.parse("{$remove:1}");

  static private boolean _isMarker( final Object obj, final JSON marker )
  {
    if (obj instanceof JSON)
    {
      final JSON j = (JSON) obj;
      if (j.isArray || j.isSimple)
        return false;
      if (j.fields.size() != 1)
        return false;
      final String name = j.fields.keySet().iterator().next();
      final String mName = marker.fields.keySet().iterator().next();
      return name.equals(mName);
    }
    return false;
  }

  /**
   * Format JSON.
   */
  @Override
  public String toString()
  {
    return toString(-1, false);
  }

  /**
   * Pretty-print JSON.
   */
  public String prettyPrint()
  {
    return toString(0, false).replaceAll(" *\n", "\n").replaceAll("\n\n", "\n").trim();
  }

  public String prettyPrint( final int level, final boolean sort )
  {
    return toString(level, sort).replaceAll(" *\n", "\n").replaceAll("\n\n", "\n").trim();
  }

  protected String toString( final int level, final boolean sort )
  {
    final StringBuilder out = new StringBuilder(256);
    if (isSimple())
    {
      final Object v = getSimpleValue_Object();
      if (v == null)
        return "null";
      enquote(v, out);
      return out.toString();
    }
    if (isArray())
    {
      indent(level, out);
      out.append("[");
      final int size = getArraySize();
      final int deeper = (level < 0) ? -1 : level + 1;
      int nSent = 0;
      if (size > 0)
        for (int n = 0;; n++)
        {
          final Object v = fields.get(String.valueOf(n));
          // support for deleted array elements is very limited, but is handled
          // for simple cases
          // TODO this shouldn't be required!!!
          if (v == null)
          {
            // keep going until 'size' elements have been written
            if (nSent >= size)
              break;
            // but not forever
            if (n > (size + 1) * 2)
              break;
            continue;
          }
          if (nSent != 0)
            out.append(',');
          nSent++;
          indent(deeper, out);
          if (v instanceof JSON)
            out.append(((JSON) v).toString(deeper, sort));
          else
          {
            indent(deeper, out);
            enquote(v, out);
          }
        }
      indent(level, out);
      out.append(']');
    }
    else if (isObject())
    {
      indent(level, out);
      out.append('{');
      final String keys[] = fields.keySet().toArray(new String[fields.size()]);
      if (sort)
        Arrays.sort(keys);
      final int deeper = (level < 0) ? -1 : level + 1;
      boolean any = false;
      for (int n = 0; n < keys.length; n++)
      {
        final String key = keys[n];
        if (key == null)
          continue;
        if (any)
          out.append(',');
        any = true;
        indent(deeper, out);
        enquote(key, out);
        out.append(':');
        if (level >= 0)
          out.append(' ');
        final Object v = fields.get(key);
        if (v instanceof JSON)
          out.append(((JSON) v).toString(deeper, sort));
        else
          enquote(v, out);
      }
      indent(level, out);
      out.append('}');
    }
    return out.toString();
  }

  private static void indent( final int level, final StringBuilder out )
  {
    if (level < 0)
      return;
    out.append('\n');
    for (int n = 0; n < level * 2; n++)
      out.append(' ');
  }

  static void enquote( final Object v, final StringBuilder out )
  {
    if (v == null || v == NULL)
    {
      out.append("null");
      return;
    }
    if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Short)
    {
      out.append(v.toString());
    }
    else if (v instanceof Float || v instanceof Double)
    {
      final double d = ((Number) v).doubleValue();
      final double da = Math.abs(d);
      if (da > 1e6 || (da < 0.1 && da > 1e-200))
      {
        String dS = String.format(Locale.US, "%.9e", d).toLowerCase();
        if (dS.contains("0e"))
          dS = dS.replaceAll("\\.?0+e", "e");
        if (dS.contains("+"))
          dS = dS.replace("+", "");
        if (dS.contains("e0") || dS.contains("e-0"))
          dS = dS.replaceAll("(e\\-?)0+", "$1");
        out.append(dS);
      }
      else
      {
        String dS = String.format(Locale.US, "%.9f", d);
        int z = dS.length() - 1;
        while (dS.charAt(z) == '0')
          z--;
        if (dS.charAt(z) == '.')
          z++;
        dS = dS.substring(0, z + 1);
        out.append(dS);
      }
    }
    else
    {
      final String strOut = v.toString();
      out.ensureCapacity(out.length() + strOut.length() + 32);
      out.append('"');
      for (final char ch : strOut.toCharArray())
        if (ch == '\\')
          out.append("\\\\");
        else if (ch == '\n')
          out.append("\\n");
        else if (ch == '"')
          out.append("\\\"");
        else if (ch == '\f')
          out.append("\\f");
        else if (ch == '\r')
          out.append("\\r");
        else if (ch == '\t')
          out.append("\\t");
        else if (ch < 32 || ch >= (char) 127)
        {
          out.append("\\u");
          String hex = Integer.toHexString(ch);
          while (hex.length() < 4)
            hex = "0" + hex;
          out.append(hex);
        }
        else
          out.append(ch);
      out.append('"');
    }
  }

  static Class<?> simpleTypes[] =
  { String.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, Character.class,
      Boolean.class, StringBuffer.class, StringBuilder.class, File.class };

  static boolean isSimple( final Object v )
  {
    for (final Class<?> t : simpleTypes)
      if (t.isInstance(v))
        return true;
    return false;
  }

  /**
   * When initializing a new object you can count on primitives being
   * initialized to zero, false, null, etc.
   */
  static public boolean isDefaultPrimitiveValue( final Object v )
  {
    if (v == null)
      return true;
    if (v instanceof Number && ((Number) v).doubleValue() == 0)
      return true;
    if (v instanceof Boolean && !((Boolean) v).booleanValue())
      return true;
    return false;
  }

  /**
   * Extracts public fields and getters.
   */
  public static JSON objectToJSON( final Object in ) throws Exception
  {
    return objectToJSON(in, false);
  }

  public static JSON objectToJSON( final Object in, final boolean skipDefaults ) throws Exception
  {
    final Set<Object> included = new HashSet<Object>();
    return objectToJSON(in, skipDefaults, included);
  }

  private static JSON objectToJSON( final Object in, final boolean skipDefaults, final Set<Object> included )
      throws Exception
  {
    if (in == null)
      return null;
    // JSON can translate directly
    if (in instanceof JSON)
      return (JSON) in;
    // handle simple values
    if (isSimple(in))
      return createSimple(in);
    // prevent infinite recursion
    if (included.contains(in))
      return null;
    included.add(in);
    // array
    final Class<?> objClass = in.getClass();
    if (objClass.isArray())
    {
      final JSON out = JSON.createArray();
      final int len = Array.getLength(in);
      for (int n = 0; n < len; n++)
        out.set(String.valueOf(n), objectToJSON(Array.get(in, n), skipDefaults, included));
      return out;
    }
    // list
    if (List.class.isAssignableFrom(objClass))
    {
      final JSON out = JSON.createArray();
      final List<?> list = (List<?>) in;
      final int len = list.size();
      for (int n = 0; n < len; n++)
        out.set(String.valueOf(n), objectToJSON(list.get(n), skipDefaults, included));
      return out;
    }
    // map
    if (Map.class.isAssignableFrom(objClass))
    {
      final Map<?, ?> map = (Map<?, ?>) in;
      final int len = map.size();
      if (len == 0)
        return JSON.createObject();
      final Object sample = map.keySet().iterator().next();
      JSON out;
      if (sample instanceof String)
      {
        out = JSON.createObject();
        for (final Object key : map.keySet())
          out.fields.put(key.toString(), objectToJSON(map.get(key), skipDefaults, included));
      }
      else
      {
        out = JSON.createArray();
        for (final Object key : map.keySet())
        {
          final JSON entry = JSON.createObject();
          entry.fields.put("key", key);
          entry.fields.put("value", objectToJSON(map.get(key), skipDefaults, included));
          out.addArrayElement(entry);
        }
      }
      return out;
    }
    // object
    final JSON out = JSON.createObject();
    for (final Field f : objClass.getFields())
    {
      if (Modifier.isStatic(f.getModifiers()))
        continue;
      final String name = f.getName();
      final Object value = f.get(in);
      final boolean shouldSkip = skipDefaults && isDefaultPrimitiveValue(value);
      if (!shouldSkip)
      {
        out.set(name, objectToJSON(value, skipDefaults, included));
      }
    }
    for (final Method m : objClass.getMethods())
    {
      if (Modifier.isStatic(m.getModifiers()))
        continue;
      if (!m.getName().startsWith("get") && !m.getName().startsWith("is"))
        continue;
      if (m.getParameterTypes().length != 0)
        continue;
      if (m.getName().equals("getClass") || m.getName().equals("get"))
        continue;
      final int baseLen = m.getName().startsWith("get") ? 3 : 2;
      final String name = Character.toLowerCase(m.getName().charAt(baseLen)) + m.getName().substring(baseLen + 1);
      Object value = m.invoke(in, new Object[0]);
      if (!isSimple(value))
        value = objectToJSON(value, skipDefaults, included);
      final boolean shouldSkip = skipDefaults && isDefaultPrimitiveValue(value);
      if (!shouldSkip)
      {
        out.set(name, value);
      }
    }
    return out;
  }

  /**
   * Convert an exception to JSON.
   */
  static public JSON describeException( final Throwable x )
  {
    final JSON out = JSON.createObject();
    out.set("message", (x.getMessage() == null) ? "" : x.getMessage());
    out.set("type", x.getClass().getSimpleName());
    final JSON trace = JSON.createArray();
    for (final StackTraceElement level : x.getStackTrace())
    {
      final JSON lvl = JSON.createObject();
      lvl.set("class", level.getClassName());
      lvl.set("file", level.getFileName());
      lvl.set("method", level.getMethodName());
      lvl.set("line", level.getLineNumber());
      trace.addArrayElement(lvl);
    }
    out.set("stack", trace);
    return out;
  }

  @Override
  public JSON clone()
  {
    final JSON out = new JSON();
    out.fields = new LinkedHashMap<String, Object>();
    out.isArray = isArray;
    out.isSimple = isSimple;
    for (final String key : fields.keySet())
    {
      final Object value = fields.get(key);
      if (value instanceof Number || value instanceof String || value instanceof Boolean)
        out.fields.put(key, value);
      else if (value instanceof JSON)
        out.fields.put(key, ((JSON) value).clone());
      else
        out.fields.put(key, value);
    }
    return out;
  }

  private static final Pattern ptn_arrayIndex = Pattern.compile("\\s*\\+?\\s*(\\d+)(\\.0*)?\\s*", Pattern.DOTALL);

  static boolean testArrayIndex( final String key )
  {
    return ptn_arrayIndex.matcher(key).matches();
  }

  static String cleanArrayIndex( final String key )
  {
    final Matcher m = ptn_arrayIndex.matcher(key);
    m.find();
    String out = m.group(1);
    if (out.startsWith("0") && out.length() > 1)
      out = out.replaceFirst("^0*([1-9].*)", "$1");
    return out;
  }

  /**
   * Sort the elements of an array.
   */
  public void sortArray( final Comparator<JSON> comparator )
  {
    if (!isArray())
      return;
    final JSON[] arr = new JSON[getArraySize()];
    for (int n = 0; n < arr.length; n++)
      arr[n] = getComplex(n);
    Arrays.sort(arr, comparator);
    final JSON arrOut = createArray();
    for (final JSON elem : arr)
      arrOut.addArrayElement(elem);
    fields = arrOut.fields;
  }

  public static void main( final String[] args )
  {
    final JSON j = JSON.parse("{list:[{a:1},{b:2},{c:3}],a:'z'}");
    System.out.print(j.prettyPrint());
  }

  @Override
  public Iterator<JSON> iterator()
  {
    return new Iterator<JSON>()
    {
      private int index = 0;

      @Override
      public void remove()
      {
      }

      @Override
      public JSON next()
      {
        final JSON value = getComplex(index);
        index++;
        return value;
      }

      @Override
      public boolean hasNext()
      {
        return index < getArraySize();
      }
    };
  }

  private static String[] fastSplit( String s, char delim )
  {
    int p = s.indexOf( delim );
    if (p == -1)
      return new String[] { s };
    return new String[] { s.substring( 0, p ), s.substring( p+1 ) };
  }
  private static boolean parseBoolean( String s, boolean d )
  {
    if (s.equalsIgnoreCase( "true" ))
      return true;
    if (s.equalsIgnoreCase( "false" ))
      return false;
    return d;
  }
  
  
  public static JSON loadFromFile( File f )
  {
    try( InputStream r = new FileInputStream( f ) )
    {
      byte buf[] = new byte[ (int)f.length() ];
      r.read( buf );
      String str = new String( buf, Charset.forName("UTF-8") ).trim();
      if (str.startsWith( "{" )  ||  str.startsWith( "[" ))
        return JSON.parse( str );
      else
        return null;
    }
    catch( IOException x )
    {
      return null;
    }
  }
}
