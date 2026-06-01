pub(crate) mod engine_state;

pub(crate) mod commands {
    pub(crate) mod body;
    pub(crate) mod engine;
    pub(crate) mod joint;
    pub(crate) mod space;
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
struct CommandHeader {
    cmd_type: Command,
    cmd_sub_type: u32,
}

#[repr(u32)]
#[derive(Debug, Copy, Clone)]
#[allow(dead_code)]
pub(crate) enum Command {
    Engine = 0,
    Space = 1,
    Body = 2,
    Joint = 3,
}

pub(crate) fn dispatch_command(buffer_ptr: *mut u64, command_offset: usize) -> usize {
    let command_header = unsafe { *(buffer_ptr.add(command_offset) as *const CommandHeader) };
    let engine = engine_state::engine();
    match command_header.cmd_type {
        Command::Engine => commands::engine::execute_subcommand(
            engine,
            command_header.cmd_sub_type,
            buffer_ptr,
            command_offset + 1,
        ),
        Command::Space => commands::space::execute_subcommand(
            engine,
            command_header.cmd_sub_type,
            buffer_ptr,
            command_offset + 1,
        ),
        Command::Body => commands::body::execute_subcommand(
            engine,
            command_header.cmd_sub_type,
            buffer_ptr,
            command_offset + 1,
        ),
        Command::Joint => commands::joint::execute_subcommand(
            engine,
            command_header.cmd_sub_type,
            buffer_ptr,
            command_offset + 1,
        ),
    }
}

#[cfg(test)]
mod tests {
  use crate::dispatch::{Command, CommandHeader};
  use std::mem;

  struct CommandBuffer {
        data: Vec<u64>,
    }

    impl CommandBuffer {
        fn new(command_count: u64) -> Self {
            let data = vec![command_count];
            Self { data }
        }

        fn add_command(&mut self, cmd_type: u32, cmd_sub_type: u32) {
            let header = CommandHeader {
                cmd_type: unsafe { mem::transmute::<u32, Command>(cmd_type) },
                cmd_sub_type,
            };

            let header_bytes = unsafe {
                std::slice::from_raw_parts(
                    &header as *const _ as *const u8,
                    size_of::<CommandHeader>(),
                )
            };

            let mut padding = vec![0u8; size_of::<u64>()];
            padding[..header_bytes.len()].copy_from_slice(header_bytes);
            let value = u64::from_le_bytes(padding[..].try_into().unwrap());
            self.data.push(value);
        }

        fn add_u64_arg(&mut self, value: u64) {
            self.data.push(value);
        }

        fn as_mut_ptr(&mut self) -> *mut u64 {
            self.data.as_mut_ptr()
        }

        fn len(&self) -> usize {
            self.data.len()
        }
    }

    #[test]
    fn test_command_header_size() {
        assert_eq!(mem::size_of::<CommandHeader>(), mem::size_of::<u64>());
    }

    #[test]
    fn test_command_enum_values() {
        assert_eq!(Command::Engine as u32, 0);
        assert_eq!(Command::Space as u32, 1);
        assert_eq!(Command::Body as u32, 2);
        assert_eq!(Command::Joint as u32, 3);
    }

    #[test]
    fn test_empty_command_buffer() {
        let buffer = CommandBuffer::new(0);

        assert_eq!(buffer.len(), 1);
        assert_eq!(buffer.data[0], 0);
    }

    #[test]
    fn test_command_buffer_alignment() {
        let mut buffer = CommandBuffer::new(1);
        buffer.add_command(Command::Engine as u32, 0);

        assert!(buffer.len() >= 2);
    }

    #[test]
    fn test_command_header_memory_layout() {
        let header = CommandHeader {
            cmd_type: Command::Space,
            cmd_sub_type: 42,
        };
        let header_bytes = unsafe {
            std::slice::from_raw_parts(&header as *const _ as *const u8, size_of::<CommandHeader>())
        };

        assert_eq!(header_bytes.len(), mem::size_of::<u64>());
    }

    #[test]
    fn test_buffer_pointer_arithmetic() {
        let mut buffer = CommandBuffer::new(1);
        let ptr = buffer.as_mut_ptr();

        assert_eq!(unsafe { *ptr }, 1);
        unsafe {
            *ptr.add(1) = 0xDEADBEEF;
        }
        assert_eq!(unsafe { *ptr.add(1) }, 0xDEADBEEF);
    }

    #[test]
    fn test_buffer_command_count_extraction() {
        let mut buffer = CommandBuffer::new(5);
        let ptr = buffer.as_mut_ptr();

        let command_count = unsafe { *ptr };
        assert_eq!(command_count, 5);
    }

    #[test]
    fn test_multiple_commands_in_buffer() {
        let mut buffer = CommandBuffer::new(3);

        buffer.add_command(Command::Engine as u32, 0);
        buffer.add_command(Command::Space as u32, 1);
        buffer.add_command(Command::Body as u32, 2);

        let ptr = buffer.as_mut_ptr();
        let count = unsafe { *ptr };
        assert_eq!(count, 3);
        assert!(buffer.len() >= 4);
    }

    #[test]
    fn test_command_offset_calculation() {
        let mut buffer = CommandBuffer::new(2);
        buffer.add_command(Command::Engine as u32, 0);
        buffer.add_command(Command::Space as u32, 1);

        let ptr = buffer.as_mut_ptr();

        assert_eq!(unsafe { *ptr.add(0) }, 2);
    }

    #[test]
    fn test_buffer_write_and_read_consistency() {
        let mut buffer = CommandBuffer::new(1);
        buffer.add_u64_arg(0x1234567890ABCDEF);

        let ptr = buffer.as_mut_ptr();
        let read_back = unsafe { *ptr.add(1) };

        assert_eq!(read_back, 0x1234567890ABCDEF);
    }

    #[test]
    fn test_command_buffer_as_slice() {
        let mut buffer = CommandBuffer::new(2);
        buffer.add_u64_arg(100);
        buffer.add_u64_arg(200);

        assert_eq!(buffer.data[0], 2);
        assert_eq!(buffer.data[1], 100);
        assert_eq!(buffer.data[2], 200);
    }

    #[test]
    fn test_command_type_discriminants_unique() {
        let engine = Command::Engine as u32;
        let space = Command::Space as u32;
        let body = Command::Body as u32;
        let joint = Command::Joint as u32;

        let mut values = vec![engine, space, body, joint];
        values.sort();
        values.dedup();

        assert_eq!(values.len(), 4);
    }

    #[test]
    fn test_max_command_count_fits() {
        let buffer = CommandBuffer::new(u64::MAX);
        assert_eq!(buffer.data[0], u64::MAX);
    }
}
