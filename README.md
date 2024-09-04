# GoFind 2.0 - SIP-Based Telecommunication Service

## Overview

This project is part of the course "Inteligência e Gestão de Redes e Computadores (IGRS)" and focuses on the specification, creation, and implementation of a telecommunication service called "GoFind 2.0" using SIP (Session Initiation Protocol) technologies. The goal is to facilitate private communication within a defined group, supporting both one-to-one calls and group conferences.

## Objectives

- Develop a SIP-based telecommunication service that allows users to find and connect with each other easily within a group.
- Ensure the service provides both direct and indirect call options, along with secure conference capabilities.
- Implement user privacy and restrict access to a specific domain (e.g., acme.pt).

## Key Features

- **User Registration:** Users can register and deregister using their SIP identities, restricted to the domain (acme.pt).
- **Direct and Indirect Calls:** Support for direct calls using SIP INVITE requests and indirect calls initiated by sending a message.
- **Conference Calling:** Users can join a predefined conference room using a SIP URI (e.g., sip:chat@acme.pt).
- **Security:** Only users belonging to the specific domain can access the service features.

## Development Methodology

- **Agile Approach:** The project follows Scrum methodology, with multiple sprints planned for iterative development.
- **Tools:** Use of SIP servlets for implementing SIP signaling and conference features. Development environment includes a virtual machine configured with necessary tools and libraries.
- **Platforms:** GitHub is used for version control, and Trello for project management and tracking tasks.
