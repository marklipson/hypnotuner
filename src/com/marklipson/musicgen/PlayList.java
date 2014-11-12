package com.marklipson.musicgen;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JOptionPane;

public class PlayList extends JButton implements Runnable
{
  HypnoTuner target;
  Thread thread;
  String name;
  boolean playing;
  JSON playlist;
  double pos;
  int index;
  boolean ended;
  
  PlayList( final HypnoTuner target )
  {
    super( "(playlist)" );
    this.target = target;
    thread = new Thread( this );
    thread.start();
    addActionListener( new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        if (playlist == null)
        {
          JOptionPane.showMessageDialog( target.frame, "click 'load' to load a playlist" );
          return;
        }
        if (ended)
          reset();
        play( ! playing );
      }
    });
  }
  public void loadPlaylist( String name, JSON data )
  {
    this.name = name;
    playlist = data.clone();
    playing = false;
    reset();
  }
  public void reset()
  {
    ended = false;
    index = -1;
    pos = 0;
  }
  public void play( boolean s )
  {
    if (ended  &&  s)
      reset();
    this.playing = s;
    if (s  &&  target.isMuted())
      target.mute( false );
    else if (! s  &&  ! target.isMuted())
      target.mute( true );
  }
  @Override
  public void run()
  {
    try
    {
      for (;;)
      {
        Thread.sleep( 100 );
        String txt = "";
        if (playlist == null)
          txt = "(playlist)";
        else if (playing)
        {
          double elapsedInSection = pos;
          for (int n=0; n < index; n++)
            elapsedInSection -= sectionLength( n );
          double remaining = sectionLength( index ) - elapsedInSection;
          int ss = (int)Math.floor( remaining );
          int mm = ss/60;
          ss %= 60;
          txt = sectionName(index) + " " + String.format("%2d:%02d",mm,ss) + " (" + name + ")";
        }
        else if (ended)
          txt = "ended (" + name + ")";
        else
          txt = "paused (" + name + ")";
        setText( txt );
        if (! playing)
          continue;
        pos += 0.1;
        int at = 0;
        double t = pos;
        for (at=0; at < playlist.getArraySize(); at++)
        {
          double len = sectionLength( at );
          t -= len;
          if (t <= 0)
            break;
        }
        if (index != at)
        {
          index = at;
          if (index < playlist.getArraySize())
            changeTo( playlist.getComplex( index ) );
          else
          {
            ended = true;
            play( false );
          }
        }
      }
    }
    catch( InterruptedException x )
    {
    }
  }
  private double sectionLength( int n )
  {
    if (n < 0  ||  n >= playlist.getArraySize())
      return 0;
    return playlist.getComplex( n ).getDouble( "duration", 10 );
  }
  private String sectionName( int n )
  {
    if (n < 0  ||  n >= playlist.getArraySize())
      return String.valueOf( n+1 );
    return playlist.getComplex( n ).getString( "name", String.valueOf( n+1 ) );
  }
  
  private void changeTo( JSON vars )
  {
    target.setSaveState( vars );
  }
}
