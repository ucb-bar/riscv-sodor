//************************************************************
// CS152 Lab 1: Tracer class for analyzing instruction mixes
//************************************************************
// TA      : Christopher Celio
// Date    : 2012 Spring
// Student :  


// ****INSTRUCTIONS*****:                                                              
//
//   Your job is to add new features to Tracer_t to provide more detailed
//   information on some of the instructions being executed.  Here are the
//   following steps required to add a new "counter":                                         
//                                                                            
//      Step 1. Add your counters to tracer.h                                 
//      Step 2. Initialize your counters to 0 in Tracer_t::start()            
//      Step 3. Increment your counters as appropriate in Tracer_t::tick()    
//      Step 4. Print out your counters in Tracer_t::print()                  
//                                                                            
//  You can grep for "Step" or "HERE" in tracer.h and tracer.cpp to find where
//  your code goes.                                                           
                                                                              
#include <stdint.h>
#include <stdio.h>
#include "emulator.h" 
       
class Tracer_t {

   public:
      Tracer_t(dat_t<32>* _inst_ptr, dat_t<1>* _stats_reg, FILE* log);
//      Tracer_t(dat_t<32>* _inst_ptr, FILE* log);
      void start();
      void tick(bool inc_inst_count);
      void stop();
      void print();

   private:
      dat_t<32>* inst_ptr;  // pointer to the Instruction Register in the processor
      dat_t<1>*  stats_reg; // pointer to the StatsEnable co-processor register cr10.
                            // Allows the software to set when to start tracking stats
                            // by calling "li x1, 1; mtpcr cr10, x1".
      FILE*      logfile;
      int        paused;
        
      struct 
      {
         uint64_t cycles;
         uint64_t inst_count;
                  
         uint64_t nop_count;
         uint64_t bubble_count;
         uint64_t ldst_count;
         uint64_t arith_count;
         uint64_t br_count;  
         uint64_t misc_count;  


         /* XXX Step 1: ADD MORE COUNTS HERE */
         uint64_t load_count;
         uint64_t store_count;
         // etc. 
      
      } trace_data;
};

