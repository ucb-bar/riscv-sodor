#*****************************************************************************
# riscv-vu_addm.S
#-----------------------------------------------------------------------------
#
# This tests a Memory Add instruction. 
#
# This test is designed for the RISC-V micro-coded processor. An unused opcode
# was stolen for this purpose.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        # Add tdat1 to tdat2, write to tdat3
        # First, load the addresses
        la x1, tdat1
        la x2, tdat2
        la x3, tdat3
        
        # perform Addm
        # provide raw binary of instruction, since the compiler
        # doesn't know about Addm...
        # R-Type:
        # | rd  |  rs1  |  rs2  |   func10   |  opcode |
        # 00011 | 00001 | 00010 | 1111111111 | 1111011 |
        # (_0001_1000_0100_0101_1111_1111_1111_1011)
        # 
   
        .word 0x1845fffb # Addm x3, x1, x2
        
        TEST_STATS_END
        
        # Check for a passed test
        lw x4, 0(x3)
        li x5, 5
        beq x4, x5, pass
        
        
        # Set the tohost register to one indicating a passed test
fail:
        addi x1, x0, 2
        mtpcr x1, cr30
        nop
        nop
        nop
1:      beq x0, x0, 1b

pass:
        addi x1, x0, 1
        mtpcr x1, cr30

        # Insert some nops so that test harness has time to stop
        # the simulation after it sees a one in tohost

        nop
        nop
        nop
        nop
        nop
        nop
        nop
        nop
        nop
        nop
        nop

1:      beq x0, x0, 1b

        TEST_RISCV_END
        
        #-------------------------------------------------------------
        # Test data
        #-------------------------------------------------------------
 
        .data
tdat:
tdat1:  .word 0x2
tdat2:  .word 0x3
tdat3:  .word 0x0ff00ff0
tdat4:  .word 0xf00ff00f
