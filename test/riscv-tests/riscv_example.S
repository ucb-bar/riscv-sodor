#*****************************************************************************
# riscv-v1_simple.S
#-----------------------------------------------------------------------------
#
# This is the most basic self checking test. If your simulator does not
# pass this then there is little chance that it will pass any of the
# more complicated self checking tests.
#

#include "test_macros.h"

        TEST_RISCV_BEGIN
        TEST_STATS_BEGIN

        # Set the tohost register to one indicating a passed test

        addi x1, x0, 1

        TEST_STATS_END

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
