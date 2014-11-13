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


Explanation:
  This program plays one base frequency (A) in one channel, and a slowly
  modulating frequency (B) in the other.  You control the base frequency
  with the first slider.  The next two sliders control the range of the
  second frequency by setting a high and a low value.  These values are
  relative to the base frequency, i.e. they are added to it.  So if you
  choose 1 and 2, and your base (A) frequency is 50Hz, the B frequency
  will vary between 51Hz and 52Hz, and you will hear 'beats' that vary
  between 1 and 2 times per second.

  You can control how rapidly the second frequency varies, with the
  next slider.

  Rather than always playing a constant frequency in one ear, which
  seemed boring, the two frequencies are slowly modulated between ears.
  The speed of this cycle is controlled by the next slider.

  You can control how much of every harmonic goes into the tones that
  are played, using the remaining sliders.


References:
* Binaural Beats - [http://en.wikipedia.org/wiki/Binaural_beats]


Main coding TODOs:
* HypnoTuner class has both tone generation and UI - split them apart
* Make it pretty
* On slow systems, or with lots of other programs running you get very
annoying clicking sounds - see top part of HypnoTuner.playSome()


Libraries:

* lib/jfugue.jar really has nothing to do with this system yet but it is a
  cool library.  It is only used in the MusicServlet class which was a
  partially formed idea with some collaborative music site in mind.  Or
  maybe a proof of concept.  I don't remember.  Anyway, it isn't used in
  HypnoTuner yet.

* "vorbis" folder comes from http://www.vorbis.com/.  The intention was
  to generate ".ogg" files that play on their own.  The code to do this is
  not ready though.

