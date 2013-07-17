#*****************************************************************************
# riscv-v2_andi.S
#-----------------------------------------------------------------------------
#
# Test andi instruction.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        #-------------------------------------------------------------
        # Logical tests
        #-------------------------------------------------------------

        TEST_IMM_OP( 2, andi, 0xff00ff00, 0xff00ff00, 0xf0f );
        TEST_IMM_OP( 3, andi, 0x000000f0, 0x0ff00ff0, 0x0f0 );
        TEST_IMM_OP( 4, andi, 0x0000000f, 0x00ff00ff, 0x70f );
        TEST_IMM_OP( 5, andi, 0x00000000, 0xf00ff00f, 0x0f0 );

        #-------------------------------------------------------------
        # Source/Destination tests
        #-------------------------------------------------------------

        TEST_IMM_SRC1_EQ_DEST( 6, andi, 0x00000000, 0xff00ff00, 0x0f0 );

        #-------------------------------------------------------------
        # Bypassing tests
        #-------------------------------------------------------------

        TEST_IMM_DEST_BYPASS( 7,  0, andi, 0x00000700, 0x0ff00ff0, 0x70f );
        TEST_IMM_DEST_BYPASS( 8,  1, andi, 0x000000f0, 0x00ff00ff, 0x0f0 );
        TEST_IMM_DEST_BYPASS( 9,  2, andi, 0xf00ff00f, 0xf00ff00f, 0xf0f );

        TEST_IMM_SRC1_BYPASS( 10, 0, andi, 0x00000700, 0x0ff00ff0, 0x70f );
        TEST_IMM_SRC1_BYPASS( 11, 1, andi, 0x000000f0, 0x00ff00ff, 0x0f0 );
        TEST_IMM_SRC1_BYPASS( 12, 2, andi, 0x0000000f, 0xf00ff00f, 0x70f );

        TEST_IMM_ZEROSRC1( 13, andi, 0, 0x0f0 );
        TEST_IMM_ZERODEST( 14, andi, 0x00ff00ff, 0x70f );

        TEST_STATS_END
        TEST_PASSFAIL
        TEST_RISCV_END
