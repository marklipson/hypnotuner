package com.marklipson.musicgen;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jfugue.Pattern;
import org.jfugue.Player;

public class MusicServlet extends HttpServlet
{
  @Override
  protected void doGet( HttpServletRequest rqst, HttpServletResponse resp ) throws ServletException, IOException
  {
    String spec = rqst.getQueryString();
    spec = URLDecoder.decode( spec, "UTF-8" );
    Player p = new Player();
    File tmp = File.createTempFile( "music", ".tmp" );
    p.saveMidi( new Pattern( spec ), tmp );
    FileInputStream in = new FileInputStream( tmp );
    System.out.println( "MIDI: " + spec );
    byte[] buf = new byte[ (int)tmp.length() ];
    in.read( buf );
    in.close();
    resp.setContentType( "audio/midi" );
    resp.getOutputStream().write( buf );
  }
}
