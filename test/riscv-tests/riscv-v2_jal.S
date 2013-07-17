#*****************************************************************************
# riscv-v2_jal.S
#-----------------------------------------------------------------------------
#
# Test jal instruction.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        #-------------------------------------------------------------
        # Test 2: Basic test
        #-------------------------------------------------------------

test_2:
        li  x28, 2
        li  ra, 0

linkaddr_2:
        jal target_2
        nop
        nop

        j fail

target_2:
        la  x2, linkaddr_2
        addi x2, x2, 4
        bne x2, ra, fail

        #-------------------------------------------------------------
        # Test delay slot instructions not executed nor bypassed
        #-------------------------------------------------------------

        TEST_CASE( 3, x2, 3, \
          li  x2, 1; \
          jal 1f; \
          addi x2, x2, 1; \
          addi x2, x2, 1; \
          addi x2, x2, 1; \
          addi x2, x2, 1; \
      1:  addi x2, x2, 1; \
          addi x2, x2, 1; \
        )

        TEST_STATS_END
        TEST_PASSFAIL
        TEST_RISCV_END
