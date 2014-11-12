hypnotuner
==========

Experimental meditation tone generator

1. Put on headphones
2. Shut down most other applications
3. Start it up
4. Twiddle the controls
5. Once you get the hang of it, record yourself a session
6. Close your eyes
7. Load back your session and groove on it

Main coding TODOs:
* HypnoTuner class has both tone generation and UI - split them apart
* Make it pretty


Libraries:

* lib/jfugue.jar really has nothing to do with this system yet but it is a
  cool library.  It is only used in the MusicServlet class which was a
  partially formed idea with some collaborative music site in mind.  Or
  maybe a proof of concept.  I don't remember.  Anyway, it isn't used in
  HypnoTuner yet.

* "vorbis" folder comes from http://www.vorbis.com/.  The intention was
  to generate ".ogg" files that play on their own.  The code to do this is
  quite a ways from being ready though.

