#define TABLE_SIZE 17 /* A small fixed table makes collisions easy to observe. */

/* used distinguishes an occupied slot from an empty slot. */
typedef struct {
  int key; /* key is the lookup identifier. */
  int value; /* value is the data associated with key. */
  int used; /* used is zero for empty and one for occupied. */
} Entry;

unsigned hash(int key) {
  return (unsigned)key % TABLE_SIZE; /* Map any key into a valid table index. */
}

void put(Entry table[], int key, int value) {
  unsigned i = hash(key); /* i is the preferred bucket for this key. */
  while (table[i].used && table[i].key != key) { /* Continue past occupied slots holding other keys. */
    i = (i + 1) % TABLE_SIZE; /* Probe the next slot, wrapping at the table boundary. */
  }
  table[i].key = key; /* Store or update the key in the selected slot. */
  table[i].value = value; /* Store the associated value. */
  table[i].used = 1; /* Mark the slot as occupied. */
}
