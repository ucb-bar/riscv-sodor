# See LICENSE for license details.

#*****************************************************************************
# simple.S
#-----------------------------------------------------------------------------
#
# This is the most basic self checking test. If your simulator does not
# pass thiss then there is little chance that it will pass any of the
# more complicated self checking tests.
#

#include "riscv_test.h"
#include "test_macros.h"

#define TEST_MOVN( testnum, result, init, a, b ) \
  TEST_CASE( testnum, x1, result, \
    li x1, MASK_XLEN(init); \
    li x2, MASK_XLEN(a); \
    li x3, MASK_XLEN(b); \
    .word 0x003100f7)

RVTEST_RV64U
RVTEST_CODE_BEGIN

  TEST_MOVN( 2, 0, 0, 1, 0 );
  TEST_MOVN( 3, 1, 0, 1, 1 );

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
