#*****************************************************************************
# riscv-vu_strcpy.S
#-----------------------------------------------------------------------------
#
# This tests a String-copy instruction.
#
# This test is designed for the RISC-V micro-coded processor. An unused opcode
# was stolen for this purpose.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        # Copy string at src_str1 to dst_str1
        # First, load the addresses
        la x1, src_str1
        la x2, dst_str1
        
        # perform strcpy
        # provide raw binary of instruction, since the compiler
        # doesn't know about strcpy...
        # R-Type:
        # | rd  |  rs1  |  rs2  |   func10   |  opcode |
        # 00010 | 00001 | 00000 | 1111111100 | 1111011 |
        # _0001_0000_0100_0001_1111_1110_0111_1011 
   
        .word 0x1041fe7b # strcpy x2, x1
        
        la x1, src_str2
        la x2, dst_str2
        .word 0x1041fe7b # strcpy x2, x1
        
        TEST_STATS_END
        
        # Check for a passed test
check1:
        la x1, dst_str1
        li x2, 0xa
        li x10, 0xf

loop1:  lw x4, 0(x1)
        bne x4, x2, fail
        beq x4, x10, done_check1
        addi x2, x2, 1
        addi x1, x1, 4
        j loop1

done_check1: 
        lw x4, 4(x1)
        bne x4, x0, fail
        

check2:
        la x1, dst_str2
        lw x4, 0(x1)
        bne x4, x0, fail
        # check one beyond to make sure we didn't overstep our bounds...
        lw x4, 4(x1)
        li x5, 0xffffffff
        bne x4, x5, fail
        
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

src_str1: .word 0xa
          .word 0xb
          .word 0xc
          .word 0xd
          .word 0xe
          .word 0xf
          .word 0x0
                  
dst_str1: .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff

src_str2: .word 0x0
                                 
dst_str2: .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
          .word 0xffffffff
     
