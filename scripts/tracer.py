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
parser.add_argument('-u', '--ucode', action='store_true',
                    help='Process the tracefile from the UCode machine')

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
if args.ucode:
    regex = "Cyc=([ 0-9]+) \[([01])\] PCReg=\[([0-9a-f]+)\] uPC=\[([0-9a-f]+)\] Bus=\[([0-9a-f]+)\] RegSel=\[([0-9a-f]+)\] RegAddr=\[([ 0-9a-f]+)\] A=\[([0-9a-f]+)\] B=\[([0-9a-f]+)\] MA=\[([0-9a-f]+)\] InstReg=\[([0-9a-f]+)\] ([F ])([M ])([X ]) ([ a-z0-9-,.()]+)"
    groupmap = {
        "cycle" : 1,
        "retire" : 2,
        "pc" : 3,
        "inst" : 11,
        "dasm" : 15,

        "upc" : 4,
        "bus" : 5,
        "regsel" : 6,
        "regaddr" : 7,
        "A" : 8,
        "B" : 9,
        "MA" : 10,
    }

else:
    regex = "Cyc=([ 0-9]+) \[([01])\] pc=\[([0-9a-f]+)] W\[r([ 0-9]+)=([0-9a-f]+)\]\[([01])\] Op1=\[r([ 0-9]+)\]\[([0-9a-f]+)\] Op2=\[r([ 0-9]+)\]\[([0-9a-f]+)\] inst=\[([0-9a-f]+)\] ([SKFH ])([BJREM ])([X ]) ([ a-z0-9-,.()]+)"

    groupmap = {
        "cycle" : 1,
        "retire" : 2,
        "pc" : 3,
        "inst" : 11,
        "dasm" : 15,

        "rd" : 4,
        "wdata" : 5,
        "wen" : 6,

        "rs1" : 7,
        "rs1_data" : 8,
        "rs2" : 9,
        "rs2_data" : 10,

        "pc_sel" : 13,
        "exception" : 14,
    }

def extract(match, key):
    return match.group(groupmap[key])



h = re.compile(regex)



for line in args.file:
    p = h.match(line)

    if p:
        # Extract useful information from a line of the trace
        cycle = int(extract(p, "cycle"))                # Cycle timestamp of this line
        retire = extract(p, "retire") == '1'            # True if instruction was retired here
        pc = int(extract(p, "pc"), 16)                  # PC of retired instruction
        inst = Instruction(int(extract(p, "inst"), 16)) # Raw instruction bits as uint
        dasm = extract(p, "dasm")                       # Diassembled instruction

        if args.ucode:
            upc = int(extract(p, "upc"), 16)          # Address of microcoded instruction
            bus = int(extract(p, "bus"), 16)          # Value on bus
            regsel = int(extract(p, "regsel"), 16)    # Which value to get the reg addr from?
            regaddr = int(extract(p, "regaddr"), 16)  # Register address
            A = int(extract(p, "A"), 16)              # A operand register
            B = int(extract(p, "B"), 16)              # B operand register
            MA = int(extract(p, "MA"), 16)            # MA memory address register
        else:
            rd = int(extract(p, "rd"))           # Destination register
            wdata = int(extract(p, "wdata"), 16) # Data to be written to destination register
            wen = extract(p, "wen") == '1'       # True of this instruction writes to a destination register

            rs1 = int(extract(p, "rs1"))                # Register operand 1 address
            rs1_data = int(extract(p, "rs1_data"), 16)  # Register operand 1 data
            rs2 = int(extract(p, "rs2"))                # Register operand 2 address
            rs2_data = int(extract(p, "rs2_data"), 16)  # Register operand 2 data

            pc_sel = extract(p, "pc_sel")               # How the next PC after this instruction is generated
            exception = extract(p, "exception")         # True if this instruction generates an exception



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

                # TODO: Track more types of instructions here?

                # Example code showing how to slice some bits out of each Instruction object
                # Bits 0-6 correspond to the opcode of instructions. Notice that inst[0:6] is 7
                # bits, as we follow verilog-style bit indexing

                bits0to6 = inst[0:6]
                # print("{0:07b}".format(bits0to6))
            else:
                n_bubbles += 1

            n_cycles = cycle - start_cycle


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
