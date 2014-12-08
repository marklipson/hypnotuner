package com.marklipson.musicgen;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class AudioFileEncoder
{
  protected static int levelToInt( double v )
  {
    if (v > 1)
      v = 1;
    if (v < -1)
      v = -1;
    int vi = (int)(v * 32767);
    return vi;
  }
  static byte[] audioToBytes( float valuesL[], float valuesR[] ) throws IOException
  {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DataOutputStream bufOut = new DataOutputStream( buf );
    for (int nv=0; nv < valuesL.length; nv++)
    {
      int vL = levelToInt( valuesL[ nv ] );
      int vR = levelToInt( valuesR[ nv ] );
      bufOut.writeShort( vL );
      bufOut.writeShort( vR );
    }
    return buf.toByteArray();
  }
  
  private File outputFile, rawFile;
  private FileOutputStream rawData;
  private long nSamples;
  private double sampleRate;
  
  public AudioFileEncoder( double sampleRate, File toFile ) throws IOException
  {
    this.sampleRate = sampleRate;
    outputFile = toFile;
    rawFile = new File( outputFile.getParentFile(), outputFile.getName() + ".raw" );
    rawData = new FileOutputStream( rawFile );
  }
  public File getOutputFile()
  {
    return outputFile;
  }
  public void write( float[] valuesL, float[] valuesR ) throws IOException
  {
    byte[] data = audioToBytes( valuesL, valuesR );
    rawData.write( data );
    nSamples += valuesL.length;
  }
  public void close() throws IOException
  {
    rawData.close();
    AudioInputStream stream = new AudioInputStream( new FileInputStream( rawFile ), new AudioFormat( (float)sampleRate, 16, 2, true, true ), nSamples );
    AudioFileFormat.Type format = AudioFileFormat.Type.WAVE;
    AudioSystem.write( stream, format, outputFile );
    rawFile.delete();
    if (! outputFile.getName().endsWith( ".wav" ))
    {
      File out = outputFile;
      File tmp = new File( outputFile.getParentFile(), outputFile.getName() + ".tmp" );
      VorbisEncoder.main( new String[] { out.toString(), tmp.toString() } );
      out.delete();
      tmp.renameTo( out );
    }
  }
}
