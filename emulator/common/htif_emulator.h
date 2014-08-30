#ifndef _HTIF_EMULATOR_H
#define _HTIF_EMULATOR_H

#include <fesvr/htif_pthread.h>


class htif_emulator_t : public htif_pthread_t
{
public:
   htif_emulator_t(uint64_t _memsz, const std::vector<std::string>& args)
     : htif_pthread_t(args), 
      state(PENDING_HOST), 
      reset(true), 
      seqno(1), 
      num_cores(1), 
      memsz(_memsz)
   {
      // the fesvr has a minimum allowed memory size
      assert ((memsz >> 20) > 0);
   }

   void set_clock_divisor(int divisor, int hold_cycles)
   {
      write_cr(-1, 63, divisor | hold_cycles << 16);
   }

   void start()
   {
      set_clock_divisor(5, 2);
      htif_pthread_t::start();
   }

   bool done();

   void tick                
   (
      // these are inputs into the htif, from the testharness
      bool csr_req_ready,
      bool mem_req_ready,
      
      bool csr_rep_valid,
      uint64_t csr_rep_bits,

      bool mem_rep_valid,
      uint64_t mem_rep_bits
   );         
            
   // Inputs to the design under test
   bool     reset; // visible to the core (allow us to initialize memory)
   bool     csr_rep_ready;

   bool     csr_req_valid;
   uint64_t csr_req_bits_addr;
   uint64_t csr_req_bits_data;
   bool     csr_req_bits_rw;
   
   bool     mem_req_valid;
   uint64_t mem_req_bits_addr;
   uint64_t mem_req_bits_data;
   bool     mem_req_bits_rw;


protected:
   virtual size_t chunk_align() { return 8; }
   virtual size_t chunk_max_size() { return 8; }

private:
enum {
   PENDING_HOST,  // waiting on the host for a packet
   PENDING_TARGET // waiting on the target for a response
};
   uint64_t state;

   uint8_t seqno;
   uint64_t num_cores;
   uint64_t memsz;
};

#endif
