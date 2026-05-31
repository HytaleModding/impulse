mod commands {
    pub(super) mod body;
    pub(super) mod engine;
    pub(super) mod joint;
    pub(super) mod space;
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
#[allow(dead_code)]
enum Command {
    Engine = 0,
    Space = 1,
    Body = 2,
    Joint = 3,
}

#[repr(C)]
#[derive(Debug, Copy, Clone)]
struct CommandHeader {
    cmd_type: Command,
    cmd_sub_type: u32,
}

#[unsafe(no_mangle)]
pub extern "C" fn process_buffer(buffer_ptr: *mut u64) {
    let command_count = unsafe { *buffer_ptr };
    let mut offset = 1;

    for _ in 0..command_count {
        let command_header = unsafe { *(buffer_ptr.add(offset) as *const CommandHeader) };
        offset += match command_header.cmd_type {
            Command::Engine => (1),
            Command::Space => commands::space::execute_subcommand(
                command_header.cmd_sub_type,
                buffer_ptr,
                offset + 1,
            ),
            Command::Body => (1),
            Command::Joint => (1),
        }
    }
}

fn execute_command(buffer_ptr: *mut u64, offset: usize) -> usize {
    0
}
