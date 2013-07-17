#*****************************************************************************
# riscv-v2_srli.S
#-----------------------------------------------------------------------------
#
# Test srli instruction.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        #-------------------------------------------------------------
        # Arithmetic tests
        #-------------------------------------------------------------

        TEST_IMM_OP( 2,  srli, 0x80000000, 0x80000000, 0  );
        TEST_IMM_OP( 3,  srli, 0x40000000, 0x80000000, 1  );
        TEST_IMM_OP( 4,  srli, 0x01000000, 0x80000000, 7  );
        TEST_IMM_OP( 5,  srli, 0x00020000, 0x80000000, 14 );
        TEST_IMM_OP( 6,  srli, 0x00000001, 0x80000001, 31 );

        TEST_IMM_OP( 7,  srli, 0xffffffff, 0xffffffff, 0  );
        TEST_IMM_OP( 8,  srli, 0x7fffffff, 0xffffffff, 1  );
        TEST_IMM_OP( 9,  srli, 0x01ffffff, 0xffffffff, 7  );
        TEST_IMM_OP( 10, srli, 0x0003ffff, 0xffffffff, 14 );
        TEST_IMM_OP( 11, srli, 0x00000001, 0xffffffff, 31 );

        TEST_IMM_OP( 12, srli, 0x21212121, 0x21212121, 0  );
        TEST_IMM_OP( 13, srli, 0x10909090, 0x21212121, 1  );
        TEST_IMM_OP( 14, srli, 0x00424242, 0x21212121, 7  );
        TEST_IMM_OP( 15, srli, 0x00008484, 0x21212121, 14 );
        TEST_IMM_OP( 16, srli, 0x00000000, 0x21212121, 31 );

        #-------------------------------------------------------------
        # Source/Destination tests
        #-------------------------------------------------------------

        TEST_IMM_SRC1_EQ_DEST( 17, srli, 0x01000000, 0x80000000, 7 );

        #-------------------------------------------------------------
        # Bypassing tests
        #-------------------------------------------------------------

        TEST_IMM_DEST_BYPASS( 18, 0, srli, 0x01000000, 0x80000000, 7  );
        TEST_IMM_DEST_BYPASS( 19, 1, srli, 0x00020000, 0x80000000, 14 );
        TEST_IMM_DEST_BYPASS( 20, 2, srli, 0x00000001, 0x80000001, 31 );

        TEST_IMM_SRC1_BYPASS( 21, 0, srli, 0x01000000, 0x80000000, 7  );
        TEST_IMM_SRC1_BYPASS( 22, 1, srli, 0x00020000, 0x80000000, 14 );
        TEST_IMM_SRC1_BYPASS( 23, 2, srli, 0x00000001, 0x80000001, 31 );

        TEST_STATS_END
        TEST_PASSFAIL
        TEST_RISCV_END
