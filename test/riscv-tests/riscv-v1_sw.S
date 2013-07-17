#*****************************************************************************
# riscv-v1_sw.S
#-----------------------------------------------------------------------------
#
# Test sw instruction. Note that since SMIPSv1 does not support a lui
# instruction we must be careful when forming addresses. We cannot use the
# standard la assembler pseudo-instruction. Instead we use the %lo(addr)
# assembler directive which tells the linker to subsitute in the low order 15
# bits of the indicated label.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        # Test 1: Store then load some data

        addi x1, x0, %lo(tdat)

        addi x2, x0, 0x0ff
        sw    x2, 0(x1)
        lw    x3, 0(x1)
        bne   x3, x2, fail

        addi x2, x0, 0x7f0
        sw    x2, 4(x1)
        lw    x3, 4(x1)
        bne   x3, x2, fail

        addi x2, x0, 0xff0
        sw    x2, 8(x1)
        lw    x3, 8(x1)
        bne   x3, x2, fail

        addi x2, x0, 0x70f
        sw    x2, 12(x1)
        lw    x3, 12(x1)
        bne   x3, x2, fail

        # Test 2: Store then load some data (negative offsets)

        addi x1, x0, %lo(tdat8)

        addi x2, x0, 0x0ff
        sw    x2, -12(x1)
        lw    x3, -12(x1)
        bne   x3, x2, fail

        addi x2, x0, 0x7f0
        sw    x2, -8(x1)
        lw    x3, -8(x1)
        bne   x3, x2, fail

        addi x2, x0, 0xff0
        sw    x2, -4(x1)
        lw    x3, -4(x1)
        bne   x3, x2, fail

        addi x2, x0, 0x70f
        sw    x2, 0(x1)
        lw    x3, 0(x1)
        bne   x3, x2, fail

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


        #-------------------------------------------------------------
        # Test data
        #-------------------------------------------------------------

        .data
tdat:
tdat1:  .word 0xdeadbeef
tdat2:  .word 0xdeadbeef
tdat3:  .word 0xdeadbeef
tdat4:  .word 0xdeadbeef
tdat5:  .word 0xdeadbeef
tdat6:  .word 0xdeadbeef
tdat7:  .word 0xdeadbeef
tdat8:  .word 0xdeadbeef
