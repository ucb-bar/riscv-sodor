#*****************************************************************************
# auipc.S
#-----------------------------------------------------------------------------
#
# Test auipc instruction.
#

#include "test_macros.h"

TEST_RISCV_BEGIN

  TEST_CASE(2, a0, -4096+8, \
    auipc a0, 0x00001; \
    jal 1f; \
    1: sub a0, ra, a0; \
  )

  TEST_CASE(3, a0, 4096+8, \
    auipc a0, 0xfffff; \
    jal 1f; \
    1: sub a0, ra, a0; \
  )

  TEST_PASSFAIL

TEST_RISCV_END

  .data

#  TEST_DATA

