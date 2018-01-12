#!/usr/bin/env python

import sys
import re

INST_RE = re.compile(r"DASM\(([0-9a-f]+)\)")

class Tracer:
    # Initialize your new counters to 0 here
    def __init__(self):
        self.inst_count = 0
        self.nop_count = 0
        self.bubble_count = 0
        self.misc_count = 0
        self.br_count = 0
        self.ldst_count = 0
        self.arith_count = 0
        self.cycles = 0

    # Increment your counters as appropriate here
    def tick(self, inst):
        opcode = inst & 0x7f
        opc_hi = (opcode >> 5) & 0x3
        opc_lo = (opcode >> 2) & 0x7

        if inst != 0x4033:
            self.inst_count += 1

        if inst == 0x13:
            self.nop_count += 1
        elif inst == 0x4033:
            self.bubble_count += 1
        elif opcode == 0x37:
            self.misc_count += 1
        elif opcode == 0x63:
            self.br_count += 1
        elif opcode == 0x03 or opcode == 0x23:
            self.ldst_count += 1
        elif opc_lo == 0x6 or opc_lo == 0x4:
            self.arith_count += 1
        else:
            self.misc_count += 1

        self.cycles += 1

    # Print your new counts here
    def print_results(self):
        print("#----------Stats--------------")
        print("#")
        print("#   CPI    : {:2.2f}".format(float(self.cycles) / self.inst_count))
        print("#   IPC    : {:2.2f}".format(float(self.inst_count) / self.cycles))
        print("#   cycles : {}".format(self.cycles))
        print("#")
        print("#   Bubbles      : {:2.3f} %".format(float(self.bubble_count) / self.cycles * 100))
        print("#   Nop instr    : {:2.3f} %".format(float(self.nop_count) / self.cycles * 100))
        print("#   Arith instr  : {:2.3f} %".format(float(self.arith_count) / self.cycles * 100))
        print("#   Ld/St instr  : {:2.3f} %".format(float(self.ldst_count) / self.cycles * 100))
        print("#   branch instr : {:2.3f} %".format(float(self.br_count) / self.cycles * 100))
        print("#   misc instr   : {:2.3f} %".format(float(self.misc_count) / self.cycles * 100))

def main():
    tracer = Tracer()

    for line in sys.stdin:
        line = line.strip()
        m = INST_RE.search(line)
        if m:
            inst = int(m.group(1), 16)
            tracer.tick(inst)
        print(line)
    
    tracer.print_results()

if __name__ == "__main__":
    main()
