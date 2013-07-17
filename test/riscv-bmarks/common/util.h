// helpful utility functions 


#ifndef __UTIL_H
#define __UTIL_H
 
//--------------------------------------------------------------------------
// Macros

// Set HOST_DEBUG to 1 if you are going to compile this for a host
// machine (ie Athena/Linux) for debug purposes and set HOST_DEBUG
// to 0 if you are compiling with the smips-gcc toolchain.

#ifndef HOST_DEBUG
#define HOST_DEBUG 0
#endif

// Set PREALLOCATE to 1 if you want to preallocate the benchmark
// function before starting stats. If you have instruction/data
// caches and you don't want to count the overhead of misses, then
// you will need to use preallocation.

#ifndef PREALLOCATE
#define PREALLOCATE 0
#endif

// Set SET_STATS to 1 if you want to carve out the piece that actually
// does the computation.

#ifndef SET_STATS
#define SET_STATS 0
#endif
 
// Set USE_SPVR_MODE to 1 if your processor supports supervisor mode.

#ifndef USE_SUPERVISOR_MODE 
#define USE_SUPERVISOR_MODE 0
#endif
                           

#if USE_SUPERVISOR_MODE
#include <stdio.h>
#include <stdlib.h>
#endif

//--------------------------------------------------------------------------
// Utility Functions

void finishTest( int toHostValue )
{
#if HOST_DEBUG
   if ( toHostValue == 1 )
      printf( "*** PASSED ***\n" );
   else
      printf( "*** FAILED *** (tohost = %d)\n", toHostValue );
   exit(0);
#elif USE_SUPERVISOR_MODE
   if ( toHostValue == 1 )
      printf( "*** PASSED *** (tohost = %d)\n", toHostValue );
   else
      printf( "*** FAILED *** (tohost = %d)\n", toHostValue );
   exit(toHostValue);
#else
   //  the fesvr uses the lsb to denote "finished"; otherwise, the fesvr will
   //  interrupt a mtpcr, cr30 to be a system call and attempt to load memory
   //  at the address pointed to by "toHostValue".
   if (toHostValue != 1)
      toHostValue = (toHostValue << 1) | 1; 
   asm( "mtpcr %0, cr30" : : "r" (toHostValue) );
   while ( 1 ) { }
#endif
}


#define rdcycle() ({ unsigned long _c; asm volatile ("rdcycle %0" : "=r"(_c) :: "memory"); _c; })
#define rdinstret() ({ unsigned long _c; asm volatile ("rdinstret %0" : "=r"(_c) :: "memory"); _c; })
                            
setStats( int enable )
{
#if ( !HOST_DEBUG && SET_STATS && !USE_SUPERVISOR_MODE)
  asm( "mtpcr %0, cr10" : : "r" (enable) );
#elif (SET_STATS && USE_SUPERVISOR_MODE)
   static unsigned long c;
   static unsigned long i;

   if (enable)
   {
      c = -rdcycle();
      i = -rdinstret();
   }
   else
   {
      c += rdcycle();
      i += rdinstret();

      printf("  stats: %lu cycles, %lu retired instructions, %lu.%02lu CPI\n", 
         c, i, c/i, (100*c/i)%100);
   }
#endif
}

  
#endif //__UTIL_H

