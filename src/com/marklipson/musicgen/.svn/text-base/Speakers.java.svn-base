package com.marklipson.musicgen;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;

public class Speakers
{
  AudioFormat format;
  SourceDataLine line;
  Speakers( double sampleRate ) throws Exception
  {
    format = new AudioFormat( (float)sampleRate, 16, 2, true, true );
    line = WavWriter.getLine( format );
    line.open();
    line.start();
  }
  /**
   * How far ahead we are buffered.
   */
  public float getBufferLevel()
  {
    float rate = line.getFormat().getSampleRate();
    int av = line.available();
    int bs = line.getBufferSize();
    return (bs - av) / rate;
  }
  public void play( float[] valuesL, float valuesR[] ) throws IOException
  {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DataOutputStream bufOut = new DataOutputStream( buf );
    for (int nv=0; nv < valuesL.length; nv++)
    {
      int vL = WavWriter.levelToInt( valuesL[ nv ] );
      int vR = WavWriter.levelToInt( valuesR[ nv ] );
      bufOut.writeShort( vL );
      bufOut.writeShort( vR );
    }
    byte[] raw = buf.toByteArray();
    line.write( raw, 0, raw.length );
  }
  public void close()
  {
    line.drain();
    line.close();
  }
}