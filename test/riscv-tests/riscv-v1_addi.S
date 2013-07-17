#*****************************************************************************
# riscv-v1_addi.S
#-----------------------------------------------------------------------------
#
#  Test addi instruction
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        # Test 1: 1 + 1 = 2

        addi x1, x0, 1
        addi x2, x1, 1
        addi x3, x0, 2
        bne   x3, x2, fail

        # Test 2: 0x0ff + 0x001 = 0x100

        addi x1, x0, 0x0ff
        addi x2, x1, 0x001
        addi x3, x0, 0x100
        bne   x3, x2, fail

        # Test 3: 0xfff + 0x001 = 0x000

        addi x1, x0, 0xfff
        addi x2, x1, 0x001
        bne   x0, x2, fail

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

        TEST_RISCV_END
