package com.marklipson.musicgen;

import org.junit.Test;
import static junit.framework.TestCase.assertEquals;

public class TestTimeFunction
{
  @Test
  public void basicParseAndEvaluate() throws Exception
  {
    assertEquals( "implicit multiplication", 2.0, TimeFunction.compile( "2t" ).evaluateMono( 1 ) );
    assertEquals( "precedence", 7.0, TimeFunction.compile( "1+2*3" ).evaluateMono( 1 ) );
    assertEquals( "precedence", 15.0, TimeFunction.compile( "1+2+3*4" ).evaluateMono( 1 ) );
    assertEquals( "precedence", 9.0, TimeFunction.compile( "1*2+3+4" ).evaluateMono( 1 ) );
    assertEquals( "precedence", 14.0, TimeFunction.compile( "1^2+2^2+3^2" ).evaluateMono( 1 ) );
    assertEquals( "more precedence", 19.0, TimeFunction.compile( "1+2*3^2" ).evaluateMono( 1 ) );
    assertEquals( "and more precedence", -0.3169, TimeFunction.compile( "sin(3^0.5t)" ).evaluateMono( 2 ), 0.001 );
    assertEquals( "trig", Math.sin(1), TimeFunction.compile( "sin(t)" ).evaluateMono( 1 ), 0.001 );
    assertEquals( "predefined", Math.sin(Math.PI*0.2), TimeFunction.compile( "sin(2pi*t)" ).evaluateMono( 0.1 ), 0.001 );
  }
  @Test
  public void vars() throws Exception
  {
    TimeFunction.CompiledFunction f = TimeFunction.compile( "t+x" );
    f.setVar( "x", 3 );
    assertEquals( 4.0, f.evaluateMono( 1 ) );
  }
  @Test
  public void stereo() throws Exception
  {
    TimeFunction.CompiledFunction f = TimeFunction.compile( "left=sin(t); right=cos(t)" );
    double lr[] = f.evaluateStereo( 1 );
    assertEquals( Math.sin(1), lr[0], 0.001 );
    assertEquals( Math.cos(1), lr[1], 0.001 );
  }
}
