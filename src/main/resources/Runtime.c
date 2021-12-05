#include <stdio.h>
#include <stdlib.h>

void* allocate(size_t size) {
    return calloc(1, size);
}

void system_println(int c) {
    printf("%d\n", c);
}

void system_write(int c) {
    putchar(c);
}

void system_flush() {
    fflush(stdout);
}

int system_read() {
    return getchar();
}
