#*****************************************************************************
# riscv-v1_bne.S
#-----------------------------------------------------------------------------
#
#  Test bne instruction
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        # Test 1: Taken?

        addi x1, x0, 1
        addi x2, x0, 2
        bne   x1, x2, 1f
        bne   x0, x1, fail
1:

        # Test 2: Not taken?

        addi x1, x0, 1
        addi x2, x0, 1
        bne   x1, x2, fail

        TEST_STATS_END

        # If we get here then we passed

        addi x1, x0, 1
        bne   x0, x1, pass

fail:
        addi x1, x0, 5
        mtpcr x1, cr30
1:      beq   x0, x0, 1b

pass:
        mtpcr x1, cr30
1:      beq   x0, x0, 1b

        .end _start
