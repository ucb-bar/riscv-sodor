# See LICENSE for license details.

#*****************************************************************************
# movn.S
#-----------------------------------------------------------------------------
#
# This is the most basic self checking test. If your simulator does not
# pass thiss then there is little chance that it will pass any of the
# more complicated self checking tests.
#

#include "riscv_test.h"
#include "test_macros.h"

// The TEST_CASE macro sets up a single assembly test.
// 1. The first argument, testnum, is the number that gets returned if the
//    test fails. You should use a unique testnum for each test.
// 2. The second argument is the register that you expect the result to
//    to be written to. In this case, it's x1.
// 3. The third argument is the value you expect the result to be.
//    The test will fail if the value in the register does not match.
// 4. The final argument is the sequence of assembly instructions for
//    the test. The first three instructions set up the destination
//    and source registers. The last instruction is the custom instruction
//    we actually want to test. Since the assembler won't recognize it,
//    we have to manually encode it using ".word".
#define TEST_MOVN( testnum, result, init, a, b ) \
  TEST_CASE( testnum, x1, result, \
    li x1, MASK_XLEN(init); \
    li x2, MASK_XLEN(a); \
    li x3, MASK_XLEN(b); \
    .word 0x003100f7)

RVTEST_RV64U
RVTEST_CODE_BEGIN

  // Test that rd is left unchanged if rs2 is 0
  TEST_MOVN( 2, 0, 0, 1, 0 );
  // Test that rd = rs1 if rs2 is not 0
  TEST_MOVN( 3, 1, 0, 1, 1 );

  TEST_PASSFAIL

RVTEST_CODE_END

  .data
RVTEST_DATA_BEGIN

  TEST_DATA

RVTEST_DATA_END
