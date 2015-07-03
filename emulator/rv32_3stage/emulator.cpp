#include "htif_emulator.h"
#include "common.h"
#include "emulator.h"
#include "Top.h" // chisel-generated code...
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

   if (vcd)
   {
      // Create a VCD file
      vcdfile = strcmp(vcd, "-") == 0 ? stdout : fopen(vcd, "w");
      assert(vcdfile);
      fprintf(vcdfile, "$scope module Testbench $end\n");
      fprintf(vcdfile, "$var reg 64 NCYCLE cycle $end\n");
      fprintf(vcdfile, "$upscope $end\n");
   }
 
   // The chisel generated code
   Top_t dut; // design under test, aka, your chisel code
   srand(random_seed);
   dut.init(random_seed != 0);
  
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

   // Instantiate HTIF
   htif = new htif_emulator_t(memory_size, std::vector<std::string>(argv + 1, argv + argc));
    
   // i'm using uint64_t for these variables, so they shouldn't be larger
   // (also consequences all the way to the Chisel memory)
   assert (dut.Top__io_htif_csr_rep_bits.width() <= 64);
   assert (dut.Top__io_htif_mem_rep_bits.width() <= 64);  

//  int htif_bits = dut.Top__io_host_in_bits.width();
//  assert(htif_bits % 8 == 0 && htif_bits <= val_n_bits());

   signal(SIGTERM, handle_sigterm);

  // reset for a few cycles to support pipelined reset
   dut.Top__io_htif_reset= LIT<1>(1);

   for (int i = 0; i < 10; i++)
   {
      dut.clock_lo(LIT<1>(1));
      dut.clock_hi(LIT<1>(1));
   }

   while (!htif->done())
   {
      dut.clock_lo(LIT<1>(0));

      // perform all fesvr HostIO to HTIFIO transformations in software
      htif->tick(
         // from tile to me,the testharness
           dut.Top__io_htif_csr_req_ready.lo_word()
         , dut.Top__io_htif_mem_req_ready.lo_word()
         
         , dut.Top__io_htif_csr_rep_valid.lo_word()
         , dut.Top__io_htif_csr_rep_bits.lo_word()
         
         , dut.Top__io_htif_mem_rep_valid.lo_word()
         , dut.Top__io_htif_mem_rep_bits.lo_word()
      );

      // send HTIF signals to the chip
      dut.Top__io_htif_csr_rep_ready = htif->csr_rep_ready;

      dut.Top__io_htif_csr_req_valid = htif->csr_req_valid;
      dut.Top__io_htif_csr_req_bits_data = htif->csr_req_bits_data;
      dut.Top__io_htif_csr_req_bits_addr = htif->csr_req_bits_addr;
      dut.Top__io_htif_csr_req_bits_rw = htif->csr_req_bits_rw;

      dut.Top__io_htif_mem_req_valid = htif->mem_req_valid;
      dut.Top__io_htif_mem_req_bits_addr = htif->mem_req_bits_addr;
      dut.Top__io_htif_mem_req_bits_data = htif->mem_req_bits_data;
      dut.Top__io_htif_mem_req_bits_rw = htif->mem_req_bits_rw;

      dut.Top__io_htif_reset = htif->reset;
         
  
      if (log)
        dut.print(logfile);

      if (vcd)
      {
        dut.dump(vcdfile, trace_count);
//        dat_dump(vcdfile, dat_t<64>(trace_count), trace_count);
//        dat_dump(vcdfile, dat_t<64>(trace_count), val_t("NCYCLE\n"));
      }

      dut.clock_hi(LIT<1>(0));
      trace_count++;

      if (max_cycles != 0 && trace_count == max_cycles)
      {
         failure = "timeout";
         break;
      }
   }

   if (vcd)
      fclose(vcdfile);


//   char buffer[100];
//   int exit_code = htif->exit_code();
//   if (!failure && (exit_code > 1))
//   {
//      sprintf(buffer, "tohost: %d", exit_code);
//      failure = buffer;
//   }
   

   if (failure)
   {
      fprintf(logfile, "*** FAILED *** (%s) after %lld cycles\n", failure, (long long)trace_count);
      return -1;
   }
   else if (htif->exit_code() <= 1)
   {
      fprintf(logfile, "*** PASSED ***\n");
   }


   delete htif;

   return 0;
}
