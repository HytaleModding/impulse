# Aligned-Buffer Protocol (ABP)

## Overview

The ffm command bridge uses a shared memory segment to exchange commands and data between Java and
the Rust command dispatcher (CD), which will then route the commands to the correct native bindings.
This
protocol is single-threaded, and it describes how Java and the Rust command dispatcher communicate.

## Aligned Memory Layout

The protocol is written to ensure 16-byte alignment of structs with a size bigger than 8-bytes with
SIMD support in mind and the 8-byte alignment of 8-byte primitives (long and double) for performance
and compatibility. The main mean to achieve this is by inserting paddings.

While these paddings reduce the effective capacity of the fixed-size memory segment, it ensures
hardware-efficient memory access that is overall worth the downsides.

# Command submission: Java → Rust CD

The protocol is used to send commands to the native backend. Any submission may contain a variable
number of commands, an N command transmission is structured in the following way:

| Description       | Offset |  Size   |
|:------------------|:------:|:-------:|
| Submission Header |   0    | 8 bytes |
| Comman 0          |   8    | I bytes |
| Command 1         |  8+I   | J bytes |
| ...               |  ...   |   ...   |
| Command N-2       |   H    |    W    |
| Command N-1       |  H+W   |    Z    |

### Submission Header:

| Description         | Offset |  Size   |
|:--------------------|:------:|:-------:|
| Total Command Count |   0    | 4 bytes |
| Padding             |   4    | 4 bytes |

The **padding** field of this header is currently unused, but it's necessary for 8-byte alignment.

## Commands:

A command follows one of the sequent structures:

| Description    | Offset |  Size   |  Type  |
|:---------------|:------:|:-------:|:------:|
| Command Header |   0    | 8 bytes |   -    |
| Payload        |   8    | N bytes | Static |

### Command Header

Holds the core information about the command.

| Description | Offset |  Size   |
|:------------|:------:|:-------:|
| Command ID  |   4    | 4 bytes |
| Padding     |   4    | 4 bytes |

The **padding** field of this header is currently unused, but it's necessary for 8-byte alignment.

The request header and the command header total to 16-byte, assuring that the command payload is
16-byte aligned.

### Command Payloads

See [Payloads](#Payloads) for more information about the payloads.

# Command response: Rust CD → Java

Used to return commands results from Rust CD to Java.

| Description     | Offset |   Size   |
|:----------------|:------:|:--------:|
| Response Header |   0    | 8 bytes  |
| Reserved        |   8    | 16 bytes | 
| Payload         |   16   | N bytes  |

**Reserved field:**  
Necessary for 16-byte alignment. In case the response type consists of a dynamic payload, its
header will be written in this reserved field, otherwise it will just be used as padding.

### Response Header

| Description    | Offset |  Size   |
|:---------------|:------:|:-------:|
| Response Flags |   0    | 4 bytes |
| Reserved       |   4    | 8 bytes |

**Response Flags:**

* 0x0: Response fully contained in this buffer.
* 0x1: Additional response data available.

**Reserved field:**
Reserved for future usages.

### Payload

See [Payloads](#Payloads) for more information about the payloads.

## Protocol Flow

The behavior of the Java command buffer changes based on the kind of command enqueued in the
buffer:

* Void Commands: Appended command to buffer. If the command doesn't fit in it, the buffer is
  first flushed and then the command is written.

* Non-Void Commands: Appended command to buffer. If the command doesn't fit in it, the buffer is
  first flushed and then the command is written. The buffer is then instantly flushed

### Handling Partial Responses

If data exceeds buffer capacity:

1) Rust writes maximum possible data.

2) Dispatcher sets Response Flag = 1.

3) Java reads all the data written on the buffer, then calls continue_response().

4) Dispatcher writes the next segment, repeating until Response Flag = 0.

Note: A chunk boundary must never occur in the middle of a primitive value (u32, u64) or a
structure.

# Payloads

The payloads are transmitted using the same standards both in the command submission and receival. A
payload is made by a static payload (eventually none) followed by a static number of dynamic
payloads:

| Description       | Offset |   Size   | Type    |
|:------------------|:------:|:--------:|:--------|
| Static Payload    |   0    | K bytes  | Static  |
| Static Padding    |   K    | Pf bytes | Static  |
| Dynamic Payload 1 |  K+Pf  | N bytes  | Dynamic |
| Dynamic Payload 2 |  K+N   |    M     | Dynamic |
| ...               |  ...   |   ...    | Static  |
| Dynamic Payload H |   W    |    Z     | Dynamic |

Where both K, H and Pf are defined at compile time and K>=0, H>=0, Pf>=0.

**Pf (Static Padding)**: it ensures that the following header has 16-byte aligned boundary + 8-byte
offset so that whatever follows it is 16-byte aligned.

## Static Payloads

These payloads are used to store data that has a size and structure known at compile time (e.g., a
constant number of primitives, structs with no dynamic fields or any combination of the two). For
this reason these payloads size and structure are known at compile time too. Since their structure
and size are known at compile time, they don't need a header.

| Description    | Offset |  Size   | Type   |
|:---------------|:------:|:-------:|:-------|
| Static Payload |   0    | K bytes | Static |

Padding is added between the various structs and primitives so that they match their necessary
alignment.

## Dynamic Payload

These payloads size and/or structure are not known at compile time, and they
may vary dynamically at runtime (e.g., strings, arrays with dynamic dimensions, structs with
arrays or other dynamic structs nested into them).

Dynamic payloads can be described as a recursive structure that allows describing a wide range of
data structures. The structure of a generic N elements dynamic payload is the following:

| Description            | Offset |   Size   |  Type   |
|:-----------------------|:------:|:--------:|:-------:|
| Dynamic Payload Header |   0    |    8     |    -    |
| Static Payload         |   8    | K bytes  | Static  |
| Dynamic payload 0      |  8+K   | N bytes  | Dynamic |
| Dymanic payload 1      | 8+K+N  | M bytes  | Dynamic |
| ...                    |  ...   |   ...    | Dynamic |
| Dynamic Padding        |   Z    | Pv bytes | Dynamic |

**Pv (Dynamic Size Padding)**: it ensures that the following command header (if any) begins
exactly at a 16-byte aligned boundary + 8-byte offset so that the next payload is 16-byte aligned.
This padding dimension may vary at runtime since the Dynamic Size Payload size(N) is dynamic.