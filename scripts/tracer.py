#!/usr/bin/python3

import argparse
import sys
import re
import instructions
from instructions import *

# Parse arguments.
parser = argparse.ArgumentParser(description="SODOR instruction trace analyzer")
parser.add_argument('file', nargs='?', type=argparse.FileType('r'),
                    default=sys.stdin,
                    help="workload.out file")
parser.add_argument

args = parser.parse_args()

# Variables for collecting stats
# We don't collect stats until the core has completed executing the bootrom,
# so collecting_stats is initialized as False
collecting_stats = False
start_cycle = 0

n_instructions = 0       # Total instructions retired while collecting_stats == True
n_arith_instructions = 0 # Total arithmetic instructions retired while collecting_stats == True
n_ldst_instructions = 0  # Total load/store instructions retired while collecting_stats == True
n_brjmp_instructions = 0 # Total branch/jump instructions retired while collecting_stats == True
n_misc_instructions = 0  # All other instructions retired while collecting_stats == True
n_bubbles = 0            # Total cycles where no instruction was committed while collecting_stats == True
n_cycles = 0             # Total cycles where collecting_stats == True

# Use Regex to decode and read each line of the trace
h = re.compile("Cyc=([ 0-9]+) \[([01])\] pc=\[([0-9a-f]+)] W\[r([ 0-9]+)=([0-9a-f]+)\]\[([01])\] Op1=\[r([ 0-9]+)\]\[([0-9a-f]+)\] Op2=\[r([ 0-9]+)\]\[([0-9a-f]+)\] inst=\[([0-9a-f]+)\] ([SKFH ])([BJREM ])([X ]) ([ a-z0-9-,.]+)")


for line in args.file:
    p = h.match(line)

    if p:
        # Extract useful information from a line of the trace
        cycle = int(p.group(1))     # Cycle timestamp of this line
        retire = p.group(2) == '1'  # True if instruction was retired here
        pc = int(p.group(3), 16)    # PC of retired instruction

        rd = int(p.group(4))        # Destination register
        wdata = int(p.group(5), 16) # Data to be written to destination register
        wen = p.group(6) == '1'     # True of this instruction writes to a destination register

        rs1 = int(p.group(7))           # Register operand 1 address
        rs1_data = int(p.group(8), 16)  # Register operand 1 data
        rs2 = int(p.group(9))           # Register operand 2 address
        rs2_data = int(p.group(10), 16) # Register operand 2 data

        inst = Instruction(int(p.group(11), 16)) # Raw instruction bits as uint

        pc_sel = p.group(13)                     # How the next PC after this instruction is generated
        exception = p.group(14)                  # True if this instruction generates an exception

        dasm = p.group(15)                       # Diassembled instruction

        # Start recording stats after we jump to the target binary at 0x8000_0000
        if retire and pc == 0x80000000:
            collecting_stats = True
            start_cycle = cycle

        if collecting_stats:
            if retire:
                n_instructions += 1
                if inst.opcode in BRJAL_OPCODES:
                    n_brjmp_instructions += 1
                elif inst.opcode in LDST_OPCODES:
                    n_ldst_instructions += 1
                elif inst.opcode in ARITH_OPCODES:
                    n_arith_instructions += 1
                else:
                    n_misc_instructions += 1
            else:
                n_bubbles += 1

            n_cycles = cycle - start_cycle

    else:
        print(line)

if (n_instructions == 0):
    sys.exit("Trace analyzer found no instructions. Are you passing in the correct trace file?")

print("""
Stats:

CPI          : {cpi:.3f}
IPC          : {ipc:.3f}
Cycles       : {cycles}
Instructions : {instructions}
Bubbles      : {bubbles}

Instruction Breakdown:
% Arithmetic  : {arith:.3f} %
% Ld/St       : {ldst:.3f} %
% Branch/Jump : {brjmp:.3f} %
% Misc.       : {misc:.3f} %
""".format(cpi=n_cycles / n_instructions,
           ipc=n_instructions / n_cycles,
           cycles=n_cycles,
           instructions=n_instructions,
           bubbles=n_bubbles,
           arith=100 * n_arith_instructions / n_instructions,
           ldst=100 * n_ldst_instructions / n_instructions,
           brjmp=100 * n_brjmp_instructions / n_instructions,
           misc=100 * n_misc_instructions / n_instructions))


