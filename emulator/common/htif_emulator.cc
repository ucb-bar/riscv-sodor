#include "htif_emulator.h"
#include <stdio.h>
#include <stdlib.h>
#include <vector>
#include <sstream>
#include <iterator>
#include "encoding.h"

// The HTIF is in one of two states: waiting on the fesvr (host),
// or waiting on the chip (target). 

void htif_emulator_t::tick(
   bool csr_req_ready,
   bool mem_req_ready,
      
   bool csr_rep_valid,
   uint64_t csr_rep_bits,

   bool mem_rep_valid,
   uint64_t mem_rep_bits
   )
{

   // default outputs 
   csr_rep_ready = true;

   csr_req_valid = false;
   csr_req_bits_addr = 0;
   csr_req_bits_data = 0;
   csr_req_bits_rw = false;
   
   mem_req_valid = false;
   mem_req_bits_addr = 0;
   mem_req_bits_data = 0;
   mem_req_bits_rw = false;

   // if we receive a response back from the chip, send it to the fesvr
   if (csr_rep_valid)
   {
      //fprintf(stderr, "\n\tcsr reply VALID, data: %lx\n\n", csr_rep_bits);
      packet_header_t ack(HTIF_CMD_ACK, seqno, 1, 0);
      send(&ack, sizeof(ack));
      send(&csr_rep_bits, sizeof(csr_rep_bits));

      state = PENDING_HOST;
   }
   if (mem_rep_valid)
   {
      //fprintf(stderr, "\n\tmem reply VALID, data: %lx\n\n", csr_rep_bits);
      packet_header_t ack(HTIF_CMD_ACK, seqno, 1, 0);
      send(&ack, sizeof(ack));
      send(&mem_rep_bits, sizeof(mem_rep_bits));

      state = PENDING_HOST;
   }

   // if we're waiting on the target, then we should quit out until we get a response
   // otherwise we'll infinite look in the "recv" function
   if (state == PENDING_TARGET)
   {
      return;
   }

   // Check if the host processed a tohost command to end the simulation.  If so,
   // we need to exit before calling recv, or the program will hang, since there
   // is no longer a target thread.
   if (exitcode) 
   {
     return;
   }

   // receive packet from fesvr
   packet_header_t hdr;
   recv(&hdr, sizeof(hdr));
   
   char buf[hdr.get_packet_size()];
   memcpy(buf, &hdr, sizeof(hdr));
   recv(buf + sizeof(hdr), hdr.get_payload_size());
   packet_t p(buf);

   assert(hdr.seqno == seqno);

   //fprintf(stderr, "\t\thtif_emulator:tick() - hdr.cmd: %d seq: %d .... datasize: %d, addr:0x%x\n"
   //   , hdr.cmd, hdr.seqno, hdr.data_size, hdr.addr);
   switch (hdr.cmd)
   {
      case HTIF_CMD_READ_MEM:
      {
         //fprintf(stderr, "\t\thtif_emulator:tick() - CMD_READ_MEM seq: %d .... datasize: %d, addr:0x%x\n"
         //   , hdr.seqno, hdr.data_size, hdr.addr);
         assert(hdr.data_size == 1);
          
         mem_req_valid = true;
         mem_req_bits_addr = hdr.addr*HTIF_DATA_ALIGN;
         mem_req_bits_data = 0;
         mem_req_bits_rw = false;

         state = PENDING_TARGET;

         break;
      }
      case HTIF_CMD_WRITE_MEM:
      {
         const uint64_t* buf = (const uint64_t*)p.get_payload();

         //fprintf(stderr, "\t\thtif_emulator:tick() - CMD_WRITE_MEM seq: %d .... datasize: %d, addr:0x%x, final: 0x%x, data: 0x%lx\n"
         //   , hdr.seqno, hdr.data_size, hdr.addr, (hdr.addr+0)*HTIF_DATA_ALIGN, buf[0]);
           
         // HTIF is sized to only handle a single double-word
         assert(hdr.data_size == 1);

         mem_req_valid = true;
         mem_req_bits_addr = hdr.addr*HTIF_DATA_ALIGN;
         mem_req_bits_data = buf[0];
         mem_req_bits_rw = true;
 
         packet_header_t ack(HTIF_CMD_ACK, seqno, 0, 0);
         send(&ack, sizeof(ack));
         break;
      }
      case HTIF_CMD_READ_CONTROL_REG:
      case HTIF_CMD_WRITE_CONTROL_REG:
      {
         reg_t coreid = hdr.addr >> 20;
         reg_t regno = hdr.addr & ((1<<20)-1);

         //fprintf(stderr, "\t\thtif_emulator:tick() - HTIF READ/WRITE CSR, seq: %d ... coreid: %d, regno: 0x%x\n",
         //   hdr.seqno, coreid, regno);

         assert(hdr.data_size == 1);
         if (coreid == 0xFFFFF) // system control register space
         {
            uint64_t scr = 0; 

            switch (regno)
            {
               case 0: scr = num_cores; break;
               case 1: scr = memsz >> 20; break;
               default: scr = -1; 
            }
            //fprintf(stderr, "\t\thtif_emulator:tick() - reading System Control Register: %d, scr: %d, packet.addr: 0x%x\n"
            //   , regno, scr, hdr.addr);
            packet_header_t ack(HTIF_CMD_ACK, seqno, 1, 0);
            send(&ack, sizeof(ack));
            send(&scr, sizeof(scr));
            break;
         }

         assert(coreid < num_cores ); 

         // send HTIF request to chip
         uint64_t new_val;
         memcpy(&new_val, p.get_payload(), sizeof(new_val));
         // handle reset specially, since it's not a register that resides on chip
         if (hdr.cmd == HTIF_CMD_WRITE_CONTROL_REG && regno == CSR_MRESET)
         {
            uint64_t old_val = reset;
            if (reset && !(new_val & 1))
            {
               reset = false;
            }
            packet_header_t ack(HTIF_CMD_ACK, seqno, 1, 0);
            send(&ack, sizeof(ack));
            send(&old_val, sizeof(old_val));
            break;
         }
         else if (regno == CSR_MHARTID)
         {
            // TODO XXX this is a hack, we should actually send this packet to the target
            uint64_t tmp = 1;
            packet_header_t ack(HTIF_CMD_ACK, seqno, 1, 0);
            send(&ack, sizeof(ack));
            send(&tmp, sizeof(tmp));
         }
         else
         {
            csr_req_valid = true;
            csr_req_bits_addr = regno;
            csr_req_bits_data = new_val;
            csr_req_bits_rw = (hdr.cmd == HTIF_CMD_WRITE_CONTROL_REG);

            state = PENDING_TARGET;
         }
         break;
      }
      default:
      fprintf(stdout, "Error, aborting in HTIF\n");
      abort();
   }
   seqno++;
}

bool htif_emulator_t::done() 
{
  return exitcode != 0;
}
 
