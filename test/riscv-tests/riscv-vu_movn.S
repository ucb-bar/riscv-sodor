#*****************************************************************************
# riscv-vu_movn.S
#-----------------------------------------------------------------------------
#
# This tests the Move Conditional on Not Zero instruction. 
#
# This test is designed for the RISC-V micro-coded processor. An unused opcode
# was stolen for this purpose.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        # Add tdat1 to tdat2, write to tdat3
        # First, load the addresses
        li x1, 888
        li x2, 1
        li x3, 999
        
        # perform movn
        # provide raw binary of instruction, since the compiler
        # doesn't know about movn...
        # R-Type:
        # | rd  |  rs1  |  rs2  |   func10   |  opcode |
        # 00011 | 00001 | 00010 | 0000001101 | 1110111 |
        # 0x6f7
        # (_0001_1000_0100_0100_0000_0110_1111_0111)
        # rd <- (rs2) ? rs1 : rd
        # x3 <- ( x2) ?  x1 : x3

   
        .word 0x184406f7 # movn x3, x1, x2
                       
        # Check for fail
        li x5, 888
        bne x3, x5, fail
                       
        li x1, 444
        li x2, 0
        li x3, 555
        .word 0x184406f7 # movn x3, x1, x2
                        
        TEST_STATS_END
        
        # Check for fail
        li x5, 555
        bne x3, x5, fail
        j pass
        
        
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
