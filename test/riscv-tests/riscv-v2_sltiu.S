#*****************************************************************************
# riscv-v2_sltiu.S
#-----------------------------------------------------------------------------
#
# Test sltiu instruction.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        #-------------------------------------------------------------
        # Arithmetic tests
        #-------------------------------------------------------------

        TEST_IMM_OP( 2,  sltiu, 0, 0x00000000, 0x000 );
        TEST_IMM_OP( 3,  sltiu, 0, 0x00000001, 0x001 );
        TEST_IMM_OP( 4,  sltiu, 1, 0x00000003, 0x007 );
        TEST_IMM_OP( 5,  sltiu, 0, 0x00000007, 0x003 );

        TEST_IMM_OP( 6,  sltiu, 1, 0x00000000, 0x800 );
        TEST_IMM_OP( 7,  sltiu, 0, 0x80000000, 0x000 );
        TEST_IMM_OP( 8,  sltiu, 1, 0x80000000, 0x800 );

        TEST_IMM_OP( 9,  sltiu, 1, 0x00000000, 0x7ff );
        TEST_IMM_OP( 10, sltiu, 0, 0x7fffffff, 0x000 );
        TEST_IMM_OP( 11, sltiu, 0, 0x7fffffff, 0x7ff );

        TEST_IMM_OP( 12, sltiu, 0, 0x80000000, 0x7ff );
        TEST_IMM_OP( 13, sltiu, 1, 0x7fffffff, 0x800 );

        TEST_IMM_OP( 14, sltiu, 1, 0x00000000, 0xfff );
        TEST_IMM_OP( 15, sltiu, 0, 0xffffffff, 0x001 );
        TEST_IMM_OP( 16, sltiu, 0, 0xffffffff, 0xfff );

        #-------------------------------------------------------------
        # Source/Destination tests
        #-------------------------------------------------------------

        TEST_IMM_SRC1_EQ_DEST( 17, sltiu, 1, 11, 13 );

        #-------------------------------------------------------------
        # Bypassing tests
        #-------------------------------------------------------------

        TEST_IMM_DEST_BYPASS( 18, 0, sltiu, 0, 15, 10 );
        TEST_IMM_DEST_BYPASS( 19, 1, sltiu, 1, 10, 16 );
        TEST_IMM_DEST_BYPASS( 20, 2, sltiu, 0, 16,  9 );

        TEST_IMM_SRC1_BYPASS( 21, 0, sltiu, 1, 11, 15 );
        TEST_IMM_SRC1_BYPASS( 22, 1, sltiu, 0, 17,  8 );
        TEST_IMM_SRC1_BYPASS( 23, 2, sltiu, 1, 12, 14 );

        TEST_STATS_END
        TEST_PASSFAIL
        TEST_RISCV_END
