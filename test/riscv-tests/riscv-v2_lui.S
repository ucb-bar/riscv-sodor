#*****************************************************************************
# riscv-v2_lui.S
#-----------------------------------------------------------------------------
#
# Test lui instruction.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        #-------------------------------------------------------------
        # Basic tests
        #-------------------------------------------------------------

        TEST_CASE( 2, x1, 0x00000000, lui x1, 0x00000 );
        TEST_CASE( 3, x1, 0xfffff000, lui x1, 0xfffff );
        TEST_CASE( 4, x1, 0x7ffff000, lui x1, 0x7ffff );
        TEST_CASE( 5, x1, 0x80000000, lui x1, 0x80000 );

        TEST_STATS_END
        TEST_PASSFAIL
        TEST_RISCV_END
