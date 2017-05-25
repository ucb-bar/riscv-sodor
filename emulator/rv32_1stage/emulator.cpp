#include "htif_emulator.h"
#include "common.h"
#include "emulator.h"
//#include "disasm.h" // disabled for now... need to update to the current ISA/ABI in common/disasm.*
#include "VTop.h" // chisel-generated code...
#include "tracer.h"
#include "verilator.h"
#include "verilated.h"
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

double sc_time_stamp ()
{
  return double( trace_count );
}

htif_emulator_t* htif;
void handle_sigterm(int sig)
{
   htif->stop();
}

uint64_t trace_count = 0;
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

   for (int i = 1; i < argc; i++)
   {
      std::string arg = argv[i];
      if (arg.substr(0, 2) == "-v")
         vcd = argv[i]+2;
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

   // The chisel generated code
   VTop dut; // design under test, aka, your chisel code
/*   srand(random_seed);
  dut.init(random_seed != 0);
   Tracer_t tracer(&dut.Top_tile_core_d__inst,
                   &dut.Top_tile_core_d_csr__reg_stats,
                   stderr);


   if (loadmem)
   {
      //  mem_t<32,32768> Top_tile_memory__data_bank1;
      // char* m = (char*)mem;
      std::ifstream in(loadmem);
      if (!in)
      {
         std::cerr << "could not open " << loadmem<< std::endl;
         exit(-1);
      }


      std::string line;
      uint64_t mem_idx = 0; // unit is 4-byte words
      while (std::getline(in, line))
      {
         // this is damn hacky, and I am sorry for that
         // lines are 32 bytes long. 
         assert (line.length()/2/4 == 4); // 4 instructions per line
         uint32_t m[4] = {0,0,0,0}; 
//         std::cerr << "$" << line << " [length:" << line.length() << "]" << std::endl;

         #define parse_nibble(c) ((c) >= 'a' ? (c)-'a'+10 : (c)-'0')
         for (ssize_t i = line.length()-2, j = 0; i >= 0; i -= 2, j++)
         {
            uint8_t byte = (parse_nibble(line[i]) << 4) | parse_nibble(line[i+1]); 
            m[j>>2] = (byte << ((j%4)*8)) | m[j>>2];
            
//            fprintf(stderr,"byte: j=%d, byte=0x%x, m[j>>2=%d]=0x%x\n", j, byte, (j>>2), m[j>>2]);
         }

         // hacky, need to keep from loading in way too much memory
         if (mem_idx < (memory_size/4)) // translate to 4-byte words
         {
//            fprintf(stderr, "   bidx: %4d , 0x%x -- b1: 0x%08x_%08x_%08x_%08x\n"
//               , mem_idx, mem_idx << 3, m[3], m[2], m[1], m[0]);
            dut.Top_tile_memory__data_bank0.put(mem_idx, LIT<32>(m[0]));
            dut.Top_tile_memory__data_bank1.put(mem_idx, LIT<32>(m[1]));
            dut.Top_tile_memory__data_bank0.put(mem_idx+1, LIT<32>(m[2]));
            dut.Top_tile_memory__data_bank1.put(mem_idx+1, LIT<32>(m[3]));
         }
         mem_idx += 2;
      }
   }

   fprintf(stderr, "Loaded memory.\n");*/

   // Instantiate HTIF
   htif = new htif_emulator_t(memory_size, std::vector<std::string>(argv + 1, argv + argc));
   fprintf(stderr, "Instantiated HTIF.\n");

   // i'm using uint64_t for these variables, so they shouldn't be larger
   // (also consequences all the way to the Chisel memory)
 //  assert (dut.Top__io_htif_csr_rep_bits.width() <= 64);
 //  assert (dut.Top__io_htif_mem_rep_bits.width() <= 64);  

//  int htif_bits = dut.Top__io_host_in_bits.width();
//  assert(htif_bits % 8 == 0 && htif_bits <= val_n_bits());

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
   //initialize memory here 

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
   top.final();
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
