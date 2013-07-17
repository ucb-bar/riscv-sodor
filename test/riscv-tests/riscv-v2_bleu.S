#*****************************************************************************
# riscv-v2_bleu.S
#-----------------------------------------------------------------------------
#
# Test bleu instruction.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        #-------------------------------------------------------------
        # Branch tests
        #-------------------------------------------------------------

        # Each test checks both forward and backward branches

        TEST_BR2_OP_TAKEN( 2, bleu, 0x00000000, 0x00000000 );
        TEST_BR2_OP_TAKEN( 3, bleu, 0x00000001, 0x00000001 );
        TEST_BR2_OP_TAKEN( 4, bleu, 0xffffffff, 0xffffffff );
        TEST_BR2_OP_TAKEN( 5, bleu, 0x00000000, 0x00000001 );
        TEST_BR2_OP_TAKEN( 6, bleu, 0xfffffffe, 0xffffffff );
        TEST_BR2_OP_TAKEN( 7, bleu, 0x00000000, 0xffffffff );

        TEST_BR2_OP_NOTTAKEN(  8, bleu, 0x00000001, 0x00000000 );
        TEST_BR2_OP_NOTTAKEN(  9, bleu, 0xffffffff, 0xfffffffe );
        TEST_BR2_OP_NOTTAKEN( 10, bleu, 0xffffffff, 0x00000000 );
        TEST_BR2_OP_NOTTAKEN( 11, bleu, 0x80000000, 0x7fffffff );

        #-------------------------------------------------------------
        # Bypassing tests
        #-------------------------------------------------------------

        TEST_BR2_SRC12_BYPASS( 12, 0, 0, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 13, 0, 1, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 14, 0, 2, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 15, 1, 0, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 16, 1, 1, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 17, 2, 0, bleu, 0xf0000000, 0xefffffff );

        TEST_BR2_SRC12_BYPASS( 18, 0, 0, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 19, 0, 1, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 20, 0, 2, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 21, 1, 0, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 22, 1, 1, bleu, 0xf0000000, 0xefffffff );
        TEST_BR2_SRC12_BYPASS( 23, 2, 0, bleu, 0xf0000000, 0xefffffff );

        #-------------------------------------------------------------
        # Test delay slot instructions not executed nor bypassed
        #-------------------------------------------------------------

        TEST_CASE( 24, $x1, 3, \
          li $x1, 1; \
          bleu $x0, $x1, 1f; \
          addi $x1, $x1, 1; \
          addi $x1, $x1, 1; \
          addi $x1, $x1, 1; \
          addi $x1, $x1, 1; \
      1:  addi $x1, $x1, 1; \
          addi $x1, $x1, 1; \
        )

        TEST_STATS_END
        TEST_PASSFAIL
        TEST_RISCV_END
