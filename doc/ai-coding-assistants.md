# AI Coding Assistants

This document provides guidance for AI tools and developers using AI assistance when contributing 
to the Impulse framework.

## Usage

Usage of AI is preferably limited in scope. Is at the Impulse maintainer's discretion whether to accept
an AI generated pull request, especially when AI appears to have produced broad design decisions, 
architecture changes, algorithm design work or code the contributor cannot clearly explain and maintain.

Impulse prefers contributions that reflect the contributor's own understanding and manual work. AI
tools are most acceptable for supporting tasks such as research, mechanical refactoring,
boilerplate generation, documentation drafts, test scaffolding or summarizing existing code. 

Usage of AI is PROHIBITED for issues labeled as "first good issue". Those are targeted for beginner 
programmers trying to enter the open source and hytale modding ecosystem.

Repeated low quality use of AI tooling may result in maintainers refusing future pull requests
from the contributor.

## Licensing and Legal Requirements

All contributions must comply with the Impulse's licensing requirements 
(See Impulse licensing rules for details).

## Signed-off-by and Developer Certificate of Origin

AI agents MUST NOT add Signed-off-by tags. Only humans can legally certify the Developer 
Certificate of Origin (DCO). The human submitter is responsible for:

    Reviewing all AI-generated code

    Ensuring compliance with licensing requirements

    Adding their own Signed-off-by tag to certify the DCO

    Taking full responsibility for the contribution

## Attribution

When AI tools contribute to the development, proper attribution helps track the evolving role of 
AI in the development process. Contributions should include an Assisted-by tag in the following format:

Assisted-by: AGENT_NAME:MODEL_VERSION [TOOL1] [TOOL2]

Where:

    AGENT_NAME is the name of the AI tool or framework

    MODEL_VERSION is the specific model version used

    [TOOL1] [TOOL2] are optional specialized analysis tools used (e.g., clang-tidy, skills) 

Basic development tools (git, gcc, make, editors) should not be listed.

Example:

Assisted-by: Claude:claude-3-opus superpowers 


