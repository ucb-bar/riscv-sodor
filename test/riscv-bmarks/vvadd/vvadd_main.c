//**************************************************************************
// Vector-vector add benchmark
//--------------------------------------------------------------------------
//
// This benchmark uses adds to vectors and writes the results to a
// third vector. The input data (and reference data) should be
// generated using the vvadd_gendata.pl perl script and dumped
// to a file named dataset1.h The smips-gcc toolchain does not
// support system calls so printf's can only be used on a host system,
// not on the smips processor simulator itself. You should not change
// anything except the HOST_DEBUG and PREALLOCATE macros for your timing
// runs.

#include "util.h"

//--------------------------------------------------------------------------
// Input/Reference Data
 
#include "dataset1.h"

//--------------------------------------------------------------------------
// Helper functions

int verify( int n, int test[], int correct[] )
{
  int i;
  for ( i = 0; i < n; i++ ) {
    if ( test[i] != correct[i] ) {
      return 2;
    }
  }
  return 1;
}

#if HOST_DEBUG 
void printArray( char name[], int n, int arr[] )
{
  int i;
  printf( " %10s :", name );
  for ( i = 0; i < n; i++ )
    printf( " %3d ", arr[i] );
  printf( "\n" );
}
#endif


//--------------------------------------------------------------------------
// vvadd function

__attribute__ ((noinline)) void vvadd( int n, int a[], int b[], int c[] ) 
{
  int i;
  for ( i = 0; i < n; i++ )
    c[i] = a[i] + b[i];
}

//--------------------------------------------------------------------------
// Main

int main( int argc, char* argv[] )
{
  
  int results_data[DATA_SIZE];

  // Output the input array

#if HOST_DEBUG
  printArray( "input1", DATA_SIZE, input1_data );
  printArray( "input2", DATA_SIZE, input2_data );
  printArray( "verify", DATA_SIZE, verify_data );
#endif

  // If needed we preallocate everything in the caches

#if PREALLOCATE
  vvadd( DATA_SIZE, input1_data, input2_data, results_data );
#endif

  // Do the vvadd

  setStats(1);
  vvadd( DATA_SIZE, input1_data, input2_data, results_data );
  setStats(0);

  // Print out the results

#if HOST_DEBUG
  printArray( "results", DATA_SIZE, results_data );
#endif

  // Check the results
 
  finishTest(verify( DATA_SIZE, results_data, verify_data ));

}
