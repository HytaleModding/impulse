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
