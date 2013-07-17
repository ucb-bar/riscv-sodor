const int REFILL_COUNT = 4; // # of cycles to refill one cache line
const int LG_MM_WORD_SIZE = 4;
const int MEM_SIZE = 512 * 1024 * 1024;

const int MM_WORD_SIZE = 1 << LG_MM_WORD_SIZE;
const int MM_CL_SIZE = REFILL_COUNT * MM_WORD_SIZE;
