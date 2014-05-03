#include "htif_emulator.h"
#include "common.h"
#include "emulator.h"
#include "disasm.h"
#include "Top.h" // chisel-generated code...
#include "tracer.h"
#include "bp.h"
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

htif_emulator_t* htif;
void handle_sigterm(int sig)
{
   htif->stop();
}

int main(int argc, char** argv)
{
   unsigned random_seed = (unsigned)time(NULL) ^ (unsigned)getpid();
   uint64_t max_cycles = 0;
   uint64_t trace_count = 0;
   int start = 0;
   bool log = false;
   const char* vcd = NULL;
   const char* loadmem = NULL;
   FILE *vcdfile = NULL, *logfile = stderr;
   const char* failure = NULL;
   disassembler disasm;
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
   if (vcd)
   {
      // Create a VCD file
      vcdfile = strcmp(vcd, "-") == 0 ? stdout : fopen(vcd, "w");
      assert(vcdfile);
      fprintf(vcdfile, "$scope module Testbench $end\n");
      fprintf(vcdfile, "$var reg %d NDISASM instruction $end\n", disasm_len*8);
      fprintf(vcdfile, "$var reg 64 NCYCLE cycle $end\n");
      fprintf(vcdfile, "$upscope $end\n");
   }
 
   // The chisel generated code
   Top_t*  dut = new Top_t(); // design under test, aka, your chisel code
   srand(random_seed);
   dut->init(random_seed != 0);
 
   // Tracer for gathering stats about CPI and instruction mix
   Tracer_t tracer(&dut->Top_tile_core_d__exe_reg_inst, 
                    &dut->Top_tile_core_d_csr__reg_stats,
                    stderr);

   // Simulated branch predictor
   struct bp_io bpio;
   // We can't go through the simulated predictor and back into CPU logic 
   // in one clock cycle, so get if_pc_next instead of if_reg_pc so we can 
   // fake it.
   bpio.if_pc_reg_ptr          = &dut->Top_tile_core_d__if_reg_pc_shadow;
   bpio.exe_reg_pc_ptr         = &dut->Top_tile_core_d__exe_reg_pc;
   bpio.exe_pc_next_ptr        = &dut->Top_tile_core_d__exe_pc_next;
   bpio.exe_br_type_ptr        = &dut->Top_tile_core_d__exe_reg_ctrl_br_type;
   bpio.exe_reg_inst_ptr       = &dut->Top_tile_core_d__exe_reg_inst;
   bpio.exe_mispredict_ptr     = &dut->Top_tile_core_d__io_ctl_mispredict;
   bpio.if_pred_taken_ptr      = &dut->Top_tile_core_d_btb__io_if_pred_taken;
   bpio.if_pred_target_ptr     = &dut->Top_tile_core_d_btb__io_if_pred_target;
   bpio.stats_reg_ptr          = &dut->Top_tile_core_d_csr__reg_stats;
   BranchPredictor* bp = BranchPredictor::make_branch_predictor( bpio );

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
            dut->Top_tile_memory__data_bank0.put(mem_idx, LIT<32>(m[0]));
            dut->Top_tile_memory__data_bank1.put(mem_idx, LIT<32>(m[1]));
            dut->Top_tile_memory__data_bank0.put(mem_idx+1, LIT<32>(m[2]));
            dut->Top_tile_memory__data_bank1.put(mem_idx+1, LIT<32>(m[3]));
         }
         mem_idx += 2;
      }
   }

   // Instantiate HTIF
   htif = new htif_emulator_t(memory_size, std::vector<std::string>(argv + 1, argv + argc));
    
   // i'm using uint64_t for these variables, so they shouldn't be larger
   // (also consequences all the way to the Chisel memory)
   assert (dut->Top__io_htif_pcr_rep_bits.width() <= 64);
   assert (dut->Top__io_htif_mem_rep_bits.width() <= 64);  

//  int htif_bits = dut->Top__io_host_in_bits.width();
//  assert(htif_bits % 8 == 0 && htif_bits <= val_n_bits());

   signal(SIGTERM, handle_sigterm);

  // reset for a few cycles to support pipelined reset
   dut->Top__io_htif_reset= LIT<1>(1);

   for (int i = 0; i < 10; i++)
   {
      dut->clock_lo(LIT<1>(1));
      dut->clock_hi(LIT<1>(1));
   }
  
   tracer.start();

   while (!htif->done())
   {
      dut->clock_lo(LIT<1>(0));
      bp->clock_lo(LIT<1>(0));

      // perform all fesvr HostIO to HTIFIO transformations in software
      htif->tick(
         // from tile to me,the testharness
           dut->Top__io_htif_pcr_req_ready.lo_word()
         , dut->Top__io_htif_mem_req_ready.lo_word()
         
         , dut->Top__io_htif_pcr_rep_valid.lo_word()
         , dut->Top__io_htif_pcr_rep_bits.lo_word()
         
         , dut->Top__io_htif_mem_rep_valid.lo_word()
         , dut->Top__io_htif_mem_rep_bits.lo_word()
      );

      // send HTIF signals to the chip
      dut->Top__io_htif_pcr_rep_ready = htif->pcr_rep_ready;

      dut->Top__io_htif_pcr_req_valid = htif->pcr_req_valid;
      dut->Top__io_htif_pcr_req_bits_data = htif->pcr_req_bits_data;
      dut->Top__io_htif_pcr_req_bits_addr = htif->pcr_req_bits_addr;
      dut->Top__io_htif_pcr_req_bits_rw = htif->pcr_req_bits_rw;

      dut->Top__io_htif_mem_req_valid = htif->mem_req_valid;
      dut->Top__io_htif_mem_req_bits_addr = htif->mem_req_bits_addr;
      dut->Top__io_htif_mem_req_bits_data = htif->mem_req_bits_data;
      dut->Top__io_htif_mem_req_bits_rw = htif->mem_req_bits_rw;

      dut->Top__io_htif_reset = htif->reset;
         
      tracer.tick(true);


      if (log || vcd)
      {
         if (log)
         {
            dut->print(logfile);
         }

         if (vcd)
         {
            insn_t insn;
            insn.bits = dut->Top_tile_core_d__exe_reg_inst.lo_word();
            std::string inst_disasm = disasm.disassemble(insn); 
            inst_disasm.resize(disasm_len, ' ');
            dat_t<disasm_len*8> disasm_dat;
            for (int i = 0; i < disasm_len; i++)
               disasm_dat = disasm_dat << 8 | LIT<8>(inst_disasm[i]);

            dut->dump(vcdfile, trace_count);
         }
      }

      bp->clock_hi(LIT<1>(0));
      dut->clock_hi(LIT<1>(0));

      trace_count++;

      if (max_cycles != 0 && trace_count == max_cycles)
      {
         failure = "timeout";
         break;
      }
   }

   tracer.stop();
   tracer.print();

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
   int retired_insts = dut->Top_tile_core_d__irt_reg.lo_word();
   int time_stamp_counter = dut->Top_tile_core_d__tsc_reg.lo_word();
   fprintf(logfile, "# IPC: %f\n", (float) retired_insts / time_stamp_counter);
#endif

   delete htif;
   delete dut;
   delete bp;

   return 0;
}
