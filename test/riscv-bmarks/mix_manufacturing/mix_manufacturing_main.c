//**************************************************************************
// Mix Manufacturing benchmark
//--------------------------------------------------------------------------
//
// Add your code here for manufacturing as many branches as possible.
// 
// The Chisel processors do not support system calls so printf's should only be
// used on a host system, not on the RISC-V processor simulator itself (unless
// you run them using the "USE_SUPERVISOR_MODE" flag.  See the Makefile for
// more information). You should not change anything except the HOST_DEBUG and
// PREALLOCATE macros for your timing runs.

#include "util.h"

//--------------------------------------------------------------------------
// Main

int main( int argc, char* argv[] )
{

  // you can declare variables here, but it counts against the 15 lines
  
  setStats(1);

  /* Insert your code here. Use only 15 C statements. No function calls etc.*/
  volatile int i = 0;
  while (i < 100)
  {
        i++;
  }

  /* Stop here */

  setStats(0);

  finishTest(1);
}
