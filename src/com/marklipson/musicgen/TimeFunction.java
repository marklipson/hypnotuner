package com.marklipson.musicgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

public class TimeFunction
{
  static private class Parser
  {
    char[] in;
    int pos;
    public Parser( String in )
    {
      this.in = in.toCharArray();
    }
    int current()
    {
      if (pos >= in.length)
        return -1;
      return in[pos];
    }
    int look( int rel )
    {
      if (pos + rel < 0  ||  pos + rel > in.length)
        return -1;
      return in[ pos + rel ];
    }
    void skip()
    {
      pos ++;
    }
    boolean skip( char ch )
    {
      if (current() == ch)
      {
        skip();
        return true;
      }
      return false;
    }
    void skipWS()
    {
      while (" \t\r\n".indexOf( current() ) != -1)
        skip();
    }
    String token( String allowed )
    {
      int p0 = pos;
      while (allowed.indexOf( current() ) != -1)
        skip();
      return new String( in, p0, pos - p0 );
    }
    @Override
    public String toString()
    {
      return new String( in, 0, pos ) + "[*]" + new String( in, pos, in.length - pos );
    }
  }
  /**
   * Faster map.
   */
  public static class MiniMap<TYPE>
  {
    Object[] targets = new Object[26*26];
    int hash( String str )
    {
      int h = 0;
      char c0 = str.charAt( 0 );
      if (c0 >= 'A'  &&  c0 <= 'Z')
        h += (c0-'A');
      else
        h += (c0-'a');
      if (str.length() > 1)
      {
        char c1 = str.charAt( 1 );
        if (c1 >= 'A'  &&  c1 <= 'Z')
          h = h*26 + (c1-'A');
        else
          h = h*26 + (c1-'a');
      }
      return h;
    }
    TYPE get( String key )
    {
      return (TYPE)targets[ hash(key) ];
    }
    void put( String key, TYPE value )
    {
      targets[ hash(key) ] = value;
    }
  }
  public static class EvalContext
  {
    private double t;
    private MiniMap<Double> vars = new MiniMap<Double>();
    public EvalContext( double t )
    {
      this.t = t;
    }
    public double getTime()
    {
      return t;
    }
    public Double getVar( String name )
    {
      return vars.get( name );
    }
    public void setVar( String name, double value )
    {
      vars.put( name, value );
    }
  }
  private static interface Node
  {
    double calculate( EvalContext t );
    Node optimize();
    void listVars( List<String> vars );
  }
  /**
   * Numeric literal.
   */
  private static class Const implements Node
  {
    private double value;
    public Const( double value )
    {
      this.value = value;
    }
    @Override
    public double calculate(EvalContext t)
    {
      return value;
    }
    @Override
    public Node optimize()
    {
      return this;
    }
    @Override
    public String toString()
    {
      return String.valueOf( value );
    }
    @Override
    public void listVars(List<String> vars)
    {
    }
  }
  /**
   * Assign a value to a variable.
   */
  private static class Assign implements Node
  {
    private String varName;
    private Node expr;
    public Assign( String varName, Node expr )
    {
      this.varName = varName;
      this.expr = expr;
    }
    @Override
    public double calculate(EvalContext t)
    {
      double v = expr.calculate( t );
      t.setVar( varName, v );
      return v;
    }
    @Override
    public Node optimize()
    {
      expr = expr.optimize();
      return this;
    }
    @Override
    public String toString()
    {
      return varName + " = " + expr;
    }
    @Override
    public void listVars(List<String> vars)
    {
      expr.listVars( vars );
    }
  }
  private static final String VAR_LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_";
  /**
   * Look up variable value.
   */
  private static class VarRef implements Node
  {
    private String varName;
    public VarRef( String varName )
    {
      this.varName = varName;
    }
    @Override
    public double calculate(EvalContext t)
    {
      Double v = t.getVar( varName );
      if (v == null)
        return 0;
      return v;
    }
    @Override
    public Node optimize()
    {
      return this;
    }
    @Override
    public String toString()
    {
      return varName;
    }
    @Override
    public void listVars(List<String> vars)
    {
      vars.add( varName );
    }
  }
  /**
   * Unary operator.
   */
  private static class UOp implements Node
  {
    private Node arg;
    private char op;
    public UOp( Node arg, char op )
    {
      this.arg = arg;
      this.op = op;
    }
    @Override
    public double calculate(EvalContext t)
    {
      double v = arg.calculate( t );
      switch( op )
      {
      case '-': return -v;
      case '+': return v;
      }
      return 0;
    }
    @Override
    public Node optimize()
    {
      arg = arg.optimize();
      if (op == '+')
        return arg;
      if (arg instanceof Const  &&  op == '-')
        return new Const( - ((Const)arg).value );
      return this;
    }
    @Override
    public String toString()
    {
      return op + arg.toString();
    }
    @Override
    public void listVars(List<String> vars)
    {
      arg.listVars( vars );
    }
  }
  /**
   * Binary operator.
   */
  private static class Op implements Node
  {
    protected Node left, right;
    private char op;
    public Op( Node left, char op, Node right )
    {
      this.left = left;
      this.right = right;
      this.op = op;
    }
    @Override
    public String toString()
    {
      return "(" + left + " " + op + " " + right + ")";
    }
    @Override
    public double calculate( EvalContext t )
    {
      double vL = left.calculate( t );
      double vR = right.calculate( t );
      switch( op )
      {
      case '+': return vL + vR;
      case '-': return vL - vR;
      case '*': return vL * vR;
      case '/': return vL / vR;
      case '^': return Math.pow( vL, vR );
      }
      return 0;
    }
    int precedence()
    {
      switch( op )
      {
      case '+': return 1;
      case '-': return 1;
      case '*': return 2;
      case '/': return 2;
      case '^': return 3;
      }
      return 0;
    }
    @Override
    public Node optimize()
    {
      left = left.optimize();
      right = right.optimize();
      if (left instanceof Const  &&  right instanceof Const)
        return new Const( calculate(null) );
      return this;
    }
    @Override
    public void listVars(List<String> vars)
    {
      left.listVars( vars );
      right.listVars( vars );
    }
  }
  /**
   * Function.
   */
  private static class Fn implements Node
  {
    protected Node arg;
    private char fn;
    public Fn( Node arg, String fn ) throws Exception
    {
      this.arg = arg;
      if (fn.equalsIgnoreCase( "sin" ))
        this.fn = 's';
      else if (fn.equalsIgnoreCase( "s" ))
        this.fn = 's';
      else if (fn.equalsIgnoreCase( "cos" ))
        this.fn = 'c';
      else if (fn.equalsIgnoreCase( "c" ))
        this.fn = 'c';
      else if (fn.equalsIgnoreCase( "log" ))
        this.fn = 'l';
      else if (fn.equalsIgnoreCase( "ln" ))
        this.fn = 'l';
      else if (fn.equalsIgnoreCase( "l" ))
        this.fn = 'l';
      else if (fn.equalsIgnoreCase( "abs" ))
        this.fn = 'a';
      else if (fn.equalsIgnoreCase( "a" ))
        this.fn = 'a';
      else
        throw new Exception( "unrecognized function: " + fn );
    }
    @Override
    public String toString()
    {
      return fn + "(" + arg + ")";
    }
    @Override
    public double calculate( EvalContext t )
    {
      double vArg = arg.calculate( t );
      switch( fn )
      {
      case 's': return (float)Math.sin( vArg );
      case 'c': return (float)Math.cos( vArg );
      case 'l': return (float)Math.log( vArg );
      case 'a': return (float)Math.abs( vArg );
      }
      return 0;
    }
    @Override
    public Node optimize()
    {
      arg = arg.optimize();
      if (arg instanceof Const)
        return new Const( calculate( null ) );
      return this;
    }
    @Override
    public void listVars(List<String> vars)
    {
      arg.listVars( vars );
    }
  }
  private static class Statements implements Node
  {
    List<Node> nodes = new ArrayList<Node>();
    @Override
    public double calculate(EvalContext t)
    {
      double v = 0;
      for (Node n : nodes)
        v = n.calculate( t );
      return v;
    }
    @Override
    public Node optimize()
    {
      for (int n=0; n < nodes.size(); n++)
        nodes.set( n,  nodes.get(n).optimize() );
      if (nodes.size() == 1)
        return nodes.get( 0 );
      return this;
    }
    @Override
    public String toString()
    {
      StringBuilder out = new StringBuilder();
      for (Node n : nodes)
      {
        out.append( n );
        out.append( ";\n" );
      }
      return out.toString();
    }
    @Override
    public void listVars(List<String> vars)
    {
      for (Node n : nodes)
        n.listVars( vars );
    }
  }
  
  /**
   * The result of compilation is one of these.  You can calculate function values at a given time,
   * and those values can take additional predefined values.
   */
  public static class CompiledFunction
  {
    private Node expr;
    private EvalContext context;
    private double scale = 1;
    
    public CompiledFunction( Node expr )
    {
      this.expr = expr;
      this.context = new EvalContext( 0 );
    }
    public void setVar( String name, double value )
    {
      context.setVar( name, value );
    }
    public void setScale(double scale)
    {
      this.scale = scale;
    }
    public void autoSetScale()
    {
      List<String> vars = new ArrayList<String>();
      expr.listVars( vars );
      Random rnd = new Random();
      double max = 0;
      scale = 1;
      for (int n=0; n < 1000; n++)
      {
        for (String var : vars)
          setVar( var, rnd.nextDouble() * 100 );
        double lr[] = evaluateStereo( rnd.nextDouble() * 100 );
        if (Math.abs(lr[0]) > max)
          max = Math.abs(lr[0]);
        if (Math.abs(lr[1]) > max)
          max = Math.abs(lr[1]);
      }
      scale = 1 / (max * 1.2);
    }
    public double evaluateMono( double t )
    {
      context.setVar( "t", t );
      return expr.calculate( context );
    }
    public double[] evaluateStereo( double t )
    {
      context.setVar( "t", t );
      double v = expr.calculate( context );
      Double vL = context.getVar( "left" );
      Double vR = context.getVar( "right" );
      if (vL == null)
        vL = v;
      if (vR == null)
        vR = v;
      return new double[] { vL*scale, vR*scale };
    }
  }
  
  /**
   * Compile an expression into a usable function object.
   */
  static public CompiledFunction compile( String expr ) throws Exception
  {
    Parser p = new Parser( expr );
    Statements ss = new Statements();
    for (;;)
    {
      Node node = parseStatement( p );
      if (node == null)
        break;
      ss.nodes.add( node );
    }
    p.skipWS();
    if (p.current() != -1)
      throw new Exception( "error" );
    if (ss.nodes.size() == 0)
      return null;
    return new CompiledFunction( ss.optimize() );
  }
  
  static private Node parseValue( Parser p ) throws Exception
  {
    p.skipWS();
    int c = p.current();
    if (c == -1)
      return null;
    // numeric constant
    if ((c == '.' && Character.isDigit( p.look(1) ))  ||  Character.isDigit( c ))
    {
      String token = p.token( ".0123456789" );
      return new Const( Float.parseFloat( token ) );
    }
    // unary
    if (c == '-'  ||  c == '+')
    {
      p.skip();
      Node sub = parseValue( p );
      return new UOp( sub, (char)c );
    }
    // parens
    if (c == '(')
    {
      p.skip();
      Node expr = parseExpr( p );
      p.skipWS();
      if (! p.skip( ')' ))
        throw new Exception( "expected ')'" );
      return expr;
    }
    // variable name
    // function call
    // predefined values
    if (Character.isAlphabetic( c )  ||  c == '_')
    {
      String token = p.token( VAR_LETTERS );
      if (token.equalsIgnoreCase( "pi" ))
        return new Const( (float)Math.PI );
      if (token.equalsIgnoreCase( "e" ))
        return new Const( (float)Math.E );
      p.skipWS();
      if (p.skip( '(' ))
      {
        Node arg = parseExpr( p );
        p.skipWS();
        if (! p.skip( ')' ))
          throw new Exception( "expected ')'" );
        return new Fn( arg, token );
      }
      else
      {
        return new VarRef( token );
      }
    }
    return null;
  }
  static Node parseExpr( Parser p ) throws Exception
  {
    Node left = parseValue( p );
    if (left == null)
      return null;
    List<Node> nodes = new ArrayList<Node>();
    Stack<Op> ops = new Stack<Op>();
    nodes.add( left );
    for (;;)
    {
      p.skipWS();
      int c = p.current();
      if (c == -1)
        break;
      int p0 = p.pos;
      if ("+-*/^".indexOf( c ) == -1)
        c = '*';
      else
        p.skip();
      Op op = new Op( null, (char)c, null );
      Node right = parseValue( p );
      if (right == null)
      {
        p.pos = p0;
        break;
      }
      nodes.add( right );
      while (ops.size() > 0  &&  op.precedence() <= ops.lastElement().precedence())
      {
        Op prevOp = ops.remove( ops.size() - 1 );
        prevOp.left = nodes.get( nodes.size() - 3 );
        prevOp.right = nodes.get( nodes.size() - 2 );
        nodes.remove( nodes.size() - 3 );
        nodes.set( nodes.size() - 2, prevOp );
      }
      ops.add( op );
    }
    while (ops.size() > 0)
    {
      Op prevOp = ops.remove( ops.size() - 1 );
      prevOp.left = nodes.get( nodes.size() - 2 );
      prevOp.right = nodes.get( nodes.size() - 1 );
      nodes.remove( nodes.size() - 1 );
      nodes.set( nodes.size() - 1, prevOp );
    }
    return nodes.get( 0 );
  }
  static Node parseStatement( Parser p ) throws Exception
  {
    p.skipWS();
    int p0 = p.pos;
    int c = p.current();
    if (Character.isAlphabetic( c )  ||  c == '_')
    {
      String token = p.token( VAR_LETTERS );
      p.skipWS();
      if (p.skip( '=' ))
      {
        Node expr = parseExpr( p );
        if (expr == null)
          throw new Exception( "Expected expression" );
        p.skipWS();
        p.skip( ';' );
        return new Assign( token, expr );
      }
      else
        p.pos = p0;
    }
    Node expr = parseExpr( p );
    if (expr == null)
      return null;
    p.skip( ';' );
    return expr;
  }
}
