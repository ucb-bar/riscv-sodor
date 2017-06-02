#include "htif_emulator.h"
#include "VTop__Dpi.h"
#include "common.h"
#if VM_TRACE
#include <verilated_vcd_c.h>
#endif
//#include "disasm.h" // disabled for now... need to update to the current ISA/ABI in common/disasm.*
#include "VTop.h" // chisel-generated code...
#include "verilator.h"
#include "verilated.h"
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

uint64_t trace_count = 0;
double sc_time_stamp ()
{
  return double( trace_count );
}

htif_emulator_t* htif;
void handle_sigterm(int sig)
{
   htif->stop();
}

int main(int argc, char** argv)
{
   unsigned random_seed = (unsigned)time(NULL) ^ (unsigned)getpid();
   uint64_t max_cycles = 0;
   int start = 0;
   bool log = false;
   const char* vcd = NULL;
   const char* loadmem = NULL;
   FILE *vcdfile = NULL, *logfile = stderr;
   const char* failure = NULL;
//   disassembler disasm;
   int memory_size = (1 << 21); // 2 MB is the smallest allowed memory by the fesvr 
 
   // for disassembly
   char inst_str[1024];
   uint64_t reg_inst = 0;
   svScope scope;
   for (int i = 1; i < argc; i++)
   {
      std::string arg = argv[i];
      if (arg.substr(0, 2) == "-v")
         vcdfile = fopen(argv[i]+2,(const char*)'w');
      else if (arg.substr(0, 2) == "-s")
         random_seed = atoi(argv[i]+2);
      else if (arg == "+verbose")
         log = true;
      else if (arg.substr(0, 12) == "+max-cycles=")
         max_cycles = atoll(argv[i]+12);
      else if (arg.substr(0, 9) == "+loadmem=")
         loadmem = argv[i]+9;
   }

   const int disasm_len = 24;

   VTop dut; // design under test, aka, your chisel code
   srand(random_seed);
   dut.init(random_seed != 0);
/*   Tracer_t tracer(&dut.Top_tile_core_d__inst,
                   &dut.Top_tile_core_d_csr__reg_stats,
                   stderr);*/

   scope = svGetScopeFromName((const char *)"TOP.Top.tile.memory.async_data");
   svSetScope(scope);
   do_readmemh((const char *)loadmem);
   fprintf(stderr, "Loaded memory.\n");

   // Instantiate HTIF
   htif = new htif_emulator_t(memory_size, std::vector<std::string>(argv + 1, argv + argc));
   fprintf(stderr, "Instantiated HTIF.\n");

#if VM_TRACE
   Verilated::traceEverOn(true); // Verilator must compute traced signals
   std::unique_ptr<VerilatedVcdFILE> vcdfd(new VerilatedVcdFILE(vcdfile));
   std::unique_ptr<VerilatedVcdC> tfp(new VerilatedVcdC(vcdfd.get()));
   if (vcdfile) {
      tile->trace(tfp.get(), 99);  // Trace 99 levels of hierarchy
      tfp->open("");
   }
#endif

   signal(SIGTERM, handle_sigterm);

   // reset for a few cycles to support pipelined reset
   for (int i = 0; i < 10; i++) {
    dut.reset = 1;
    dut.clock = 0;
    dut.eval();
    dut.clock = 1;
    dut.eval();
    dut.reset = 0;
  }



//   tracer.start();
   while (!htif->done() && !Verilated::gotFinish()) {
      dut.clock = 0;
      // perform all fesvr HostIO to HTIFIO transformations in software
      htif->tick(
         // from tile to me,the testharness
           dut.io_htif_csr_req_ready
         , dut.io_htif_mem_req_ready
         
         , dut.io_htif_csr_rep_valid
         , dut.io_htif_csr_rep_bits
         
         , dut.io_htif_mem_rep_valid
         , dut.io_htif_mem_rep_bits
      );

      dut.io_htif_csr_rep_ready = htif->csr_rep_ready;

      dut.io_htif_csr_req_valid = htif->csr_req_valid;
      dut.io_htif_csr_req_bits_data = htif->csr_req_bits_data;
      dut.io_htif_csr_req_bits_addr = htif->csr_req_bits_addr;
      dut.io_htif_csr_req_bits_rw = htif->csr_req_bits_rw;
      dut.io_htif_ipi_req_valid = false;

      dut.io_htif_mem_req_valid = htif->mem_req_valid;
      dut.io_htif_mem_req_bits_addr = htif->mem_req_bits_addr;
      dut.io_htif_mem_req_bits_data = htif->mem_req_bits_data;
      dut.io_htif_mem_req_bits_rw = htif->mem_req_bits_rw;

      dut.io_htif_reset = htif->reset;

      dut.eval();
#if VM_TRACE
      bool dump = tfp && trace_count >= start;
      if (dump)
         tfp->dump(static_cast<vluint64_t>(trace_count * 2));
#endif

      dut.clock = 1;
      dut.eval();
#if VM_TRACE
      if (dump)
         tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
#endif
      trace_count++;
      if (max_cycles != 0 && trace_count == max_cycles)
      {
         failure = "timeout";
         break;
      }
   }

   // tracer.stop();
   // tracer.print();
   dut.final();
   if (vcd)
      fclose(vcdfile);

   if (failure)
   {
      fprintf(logfile, "*** FAILED *** (%s) after %lld cycles\n", failure, (long long)trace_count);
      return -1;
   }
   else if (htif->exit_code() <= 1)
   {
      fprintf(logfile, "*** PASSED ***\n");
   }
   else 
   {
     return htif->exit_code();
   }

#if 0
   // XXX Top_tile_core_d__irt_reg does not exists
   int retired_insts = dut.Top_tile_core_d__irt_reg.lo_word();
   int time_stamp_counter = dut.Top_tile_core_d__tsc_reg.lo_word();
   fprintf(logfile, "# IPC: %f\n", (float) retired_insts / time_stamp_counter);
#endif

   delete htif;

   return 0;
}
