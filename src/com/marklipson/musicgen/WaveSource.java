package com.marklipson.musicgen;

/**
 * Generates stereo tones based on a number of parameters.
 */
public class WaveSource
{
  // SETTINGS
  
  // play position since start (samples)
  private long n = 0;
  // sampling rate, samples per second
  private int rate = 44100;
  // duration of fade-in / fade-out
  private double fade_s = 10;

  /**
   * Base frequency.
   */
  public SmoothValue vA;
  /**
   * Range of deltas to secondary frequency.  Secondary frequency varies from vA+vBlo to vA+vBhi.
   */
  public SmoothValue vBlo, vBhi;
  /**
   * How rapidly to swap vA and vB between left and right audio channels.
   */
  public SmoothValue vBalCycle;
  /**
   * How rapidly to vary vB between the low and high delta.
   */
  public SmoothValue vBeatCycle;
  /**
   * How much of each harmonic to include in the tones.
   */
  public SmoothValue vH[];
  /**
   * Custom functions.
   */
  private TimeFunction.CompiledFunction customFunction;
  private TimeFunction.CompiledFunction prevCustomFunction;
  private SmoothValue customMix, customChange;
  public SmoothValue customLevel;

  /**
   * Access to the current real balance phase.
   */
  public TrackValue track_balance = new TrackValue();
  /**
   * Access to the current real 'beat cycle' phase.
   */
  public TrackValue track_beat = new TrackValue();

  // for muting sound
  private SmoothValue muted = new SmoothValue( 0.4 );
  
  // the real output time depends on how much the audio target has buffered
  private AudioTarget audioTarget;
  
  // - time values
  private double tBase = 0;
  private double tL = 0, tR = 0;
  private double tBeat = 0;
  private double tBal = 0;
  private double tFade = 0;

  // - multiplier for sample number to get a 1Hz wave
  private double dt1;

  // time data
  private long tStart = System.nanoTime();
  private long tOffset = 0;

  
  public WaveSource( int nHarmonics )
  {
    dt1 = Math.PI * 2 / rate;
    vA = new SmoothValue( 0.4 );
    vBlo = new SmoothValue( 0.4, false );
    vBhi = new SmoothValue( 0.4, false );
    vBalCycle = new SmoothValue( 0.4 );
    vBeatCycle = new SmoothValue( 0.4 );
    // harmonics
    vH = new SmoothValue[ nHarmonics ];
    for (int nh=0; nh < nHarmonics; nh++)
      vH[nh] = new SmoothValue( 0.4, false );
    //
    customMix = new SmoothValue( 0.4, false );
    customChange = new SmoothValue( 0.1, false );
    customLevel = new SmoothValue( 0.4, false );
  }
  
  /**
   * Time for signal currently being generated.
   */
  double tGen()
  {
    return (double)n / rate;
  }
  /**
   * Time in signal currently being played.
   */
  double tReal()
  {
    long tN = System.nanoTime() - tStart + tOffset;
    return tN/1e9;
  }
  /**
   * Sampling rate.
   */
  public int getRate()
  {
    return rate;
  }
  
  public void setCustomFunction( TimeFunction.CompiledFunction function )
  {
    prevCustomFunction = customFunction;
    customFunction = function;
    // pan gradually from old to new function
    customChange.setValue( 1, true );
    customChange.setValue( 0 );
    // pan gradually from standard to custom waveform
    customMix.setValue( 1 );
  }

  public void setAudioTarget( AudioTarget audioTarget )
  {
    this.audioTarget = audioTarget;
  }
  public boolean isMuted()
  {
    return muted.getValue() < 0.1;
  }
  public void mute( boolean s )
  {
    muted.setValue( s ? 0 : 1 );
  }
  public void fade( boolean s )
  {
    if (s)
      tFade = tGen();
    else
      tFade = 0;
  }
  public boolean isFading()
  {
    return tFade > 0;
  }
  
  public float[][] generate( int nSamples )
  {
    // - output values
    float[] vL = new float[ nSamples ];
    float[] vR = new float[ nSamples ];
    {
      long tReal = System.nanoTime() - tStart;
      float bufferLevel = 0;
      if (audioTarget != null)
        bufferLevel = audioTarget.getBufferLevel();
      long tGen = (long)((tGen() - bufferLevel) * 1e9);
      tOffset = tGen - tReal;
      //System.out.println( "tOffs: " + tOffset );
    }
    for (int index=0; index < vL.length; index++, n++)
    {
      tBase += dt1;
      // cycle tone B between hi and lo, every (beatCycle)
      double beatCycle = vBeatCycle.getValue();
      tBeat += dt1 / beatCycle;
      track_beat.store( tBeat );
      double slowVariation = Math.sin( tBeat );
      // base frequency
      double fL = vA.getValue();
      tL += fL * dt1;
      double L = waveform( tL );
      // - range of frequency delta for right channel
      double lowBeatHz = vBlo.getValue();
      double highBeatHz = vBhi.getValue();
      double midBeatHz = (lowBeatHz + highBeatHz) / 2;
      double rBeat = highBeatHz - midBeatHz;
      double fR = fL + midBeatHz + rBeat * slowVariation;
      tR += fR * dt1;
      double R = waveform( tR );
      // - swing balance back and forth in a cycle lasting this many seconds
      double balanceSwingCycle = vBalCycle.getValue();
      tBal += dt1 / balanceSwingCycle;
      track_balance.store( tBal );
      double bL = (Math.sin( tBal ) + 1) / 2;
      double bR = 1 - bL;
      if (customFunction != null)
      {
        double vCustom = customMix.getValue() * customLevel.getValue();
        if (vCustom > 0.00001)
        {
          double vChange = customChange.getValue();
          customFunction.setVar( "a", fL );
          customFunction.setVar( "b", fR );
          customFunction.setVar( "ta", tL );
          customFunction.setVar( "tb", tR );
          double cLR[] = customFunction.evaluateStereo( tBase );
          if (vChange > 0.0001  &&  prevCustomFunction != null)
          {
            // function is being changed
            double cLR2[] = prevCustomFunction.evaluateStereo( tL );
            double vC1 = 1 - vChange;
            cLR[0] = cLR[0] * vC1 + cLR2[0] * vChange;
            cLR[1] = cLR[1] * vC1 + cLR2[1] * vChange;
          }
          double vAlt = 1 - vCustom;
          L = L * vAlt + cLR[0] * vCustom;
          R = R * vAlt + cLR[1] * vCustom;
        }
      }
      double L1 = L * bL + R * bR;
      double R1 = R * bL + L * bR;
      
      vL[index] = (float)(L1);
      vR[index] = (float)(R1);
      // fade in/out
      double tS = tGen();
      double fade = softenEdges( tS, fade_s );
      if (tFade > 0)
      {
        double tNow = (double)n/rate;
        double tF = tNow - tFade;
        fade *= Math.pow( 0.15, tF );
      }
      fade *= muted.getValue();
      vL[index] *= fade;
      vR[index] *= fade;
    }
    return new float[][] { vL, vR };
  }

  double waveform( double t )
  {
    double vW = 0;
    double tot = 0;
    for (int nh=0; nh < vH.length; nh++)
    {
      double level = vH[nh].getValue();
      if (level > 0.000001)
      {
        double h = Math.sin( t * (nh+1) );
        vW += h * level;
        tot += level;
      }
    }
    if (tot == 0)
      tot = 1;
    return vW / tot;
  }
    
  /**
   * t == 0 to 1  (0 = start, 1 = end)
   * width == duration of fade-in/out, i.e. 0.01
   * return == 0 to 1 (0 = silent, 1 = full volume)
   */
  static double softenEdges( double t, double width )
  {
    if (t < width)
      return t / width;
    return 1;
  }
  
  /**
   * Values are not generated in real time, so we save some of them, to know what they actually are in real time.
   */
  class TrackValue
  {
    double list[] = new double[ 500 ];
    int index( double t )
    {
      long tN = (long)(t / 100);
      return (int)(tN % list.length);
    }
    void store( double v )
    {
      double t = tGen();
      list[ index(t) ] = v;
    }
    double retrieve()
    {
      double t = tReal();
      return list[ index(t) ];
    }
  }
  
  /**
   * Gently modifies values.  Use {@link SmoothValue#setValue(double)} to set the target value,
   * and {@link SmoothValue#getValue()} will smoothly shift to that value.
   */
  class SmoothValue
  {
    double vNow = Double.NaN;
    double vTarget;
    double tPrev;
    double speed;
    boolean geometric;
    SmoothValue( double speed )
    {
      this( speed, true );
    }
    SmoothValue( double speed, boolean geometric )
    {
      this.speed = speed;
      this.geometric = geometric;
      tPrev = tGen();
    }
    double getValue()
    {
      double tNow = tGen();
      double tE = tNow - tPrev;
      tPrev = tNow;
      if (Double.isNaN( vNow ))
        ;
      else if (Math.abs( vTarget - vNow ) > 0.000001)
      {
        double approach = 1 - Math.pow( speed, tE );
        /*
        if (geometric)
        {
          double delta = vTarget / vNow;
          vNow = vNow * delta * approach;
        }
        else
        */
        {
          double delta = vTarget - vNow;
          vNow += delta * approach;
        }
      }
      else
        vNow = vTarget;
      return vNow;
    }
    void setValue( double v )
    {
      if (Double.isNaN( vNow ))
        vNow = v;
      vTarget = v;
    }
    void setValue( double v, boolean immediately )
    {
      if (immediately)
        vNow = vTarget = v;
      else
        setValue( v );
    }
  }
  
  /**
   * Information about where audio is being sent.
   */
  public interface AudioTarget
  {
    /**
     * Number of seconds of audio signal that have been buffered and have yet to be played.
     */
    float getBufferLevel();
  }
  
  /*
  public static void main(String[] args)
  {
    WaveSource w = new WaveSource( 16 );
    w.vA.setValue( 40 );
    w.vBlo.setValue( 0.2 );
    w.vBhi.setValue( 0.8 );
    w.vH[0].setValue( 1 );
    w.vH[1].setValue( 0.2 );
    w.vH[3].setValue( 0.1 );
    w.vH[4].setValue( 0.1 );
    w.vH[5].setValue( 0.1 );
    w.vH[6].setValue( 0.1 );
    long t0 = System.nanoTime();
    for (int n=0; n < 1000000; n++)
      w.waveform( n/1000.0 );
    double tE = (System.nanoTime() - t0) / 1e9;
    System.out.println( tE );
  }
  */
}
