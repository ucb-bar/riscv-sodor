#*****************************************************************************
# riscv-v1_lw.S
#-----------------------------------------------------------------------------
#
# Test lw instruction. Note that since SMIPSv1 does not support a lui
# instruction we must be careful when forming addresses. We cannot use the
# standard la assembler pseudo-instruction. Instead we use the %lo(addr)
# assembler directive which tells the linker to subsitute in the low order 15
# bits of the indicated label.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        # Test 1: Load some data

        addi x1, x0, %lo(tdat)

        lw    x2, 0(x1)
        addi x3, x0, 0x0ff
        bne   x3, x2, fail

        lw    x2, 4(x1)
        addi x3, x0, 0x7f0
        bne   x3, x2, fail

        lw    x2, 8(x1)
        addi x3, x0, 0x3f3
        bne   x3, x2, fail

        lw    x2, 12(x1)
        addi x3, x0, 0x70f
        bne   x3, x2, fail

        # Test 2: Load some data with negative offsets

        addi x1, x0, %lo(tdat4)

        lw    x2, -12(x1)
        addi x3, x0, 0x0ff
        bne   x3, x2, fail

        lw    x2, -8(x1)
        addi x3, x0, 0x7f0
        bne   x3, x2, fail

        lw    x2, -4(x1)
        addi x3, x0, 0x3f3
        bne   x3, x2, fail

        lw    x2, 0(x1)
        addi x3, x0, 0x70f
        bne   x3, x2, fail

        TEST_STATS_END

        # If we get here then we passed

        addi x1, x0, 1
        bne   x0, x1, pass

fail:
        addi x1, x0, 2
        mtpcr x1, cr30
1:      beq   x0, x0, 1b

pass:
        mtpcr x1, cr30
1:      beq   x0, x0, 1b

        .end _start

        #-------------------------------------------------------------
        # Test data
        #-------------------------------------------------------------

        .data
tdat:
tdat1:  .word 0x000000ff
tdat2:  .word 0x000007f0
tdat3:  .word 0x000003f3
tdat4:  .word 0x0000070f
