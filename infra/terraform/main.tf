terraform {
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

provider "docker" {
  host = "npipe:////./pipe/docker_engine"
}

resource "docker_network" "app" {
  name = "orderhub-net"
}

resource "docker_image" "server" {
  name = "lscr.io/linuxserver/openssh-server:latest"
}

resource "docker_container" "server" {
  name  = "orderhub-server"
  image = docker_image.server.image_id

  networks_advanced {
    name = docker_network.app.name
  }

  env = [
    "USER_NAME=deploy",
    "PASSWORD_ACCESS=true",
    "USER_PASSWORD=${var.server_password}",
    "SUDO_ACCESS=true",
  ]

  ports {
    internal = 2222
    external = 2222
  }
}

variable "server_password" {
  type      = string
  sensitive = true
}

output "server_ssh" {
  value = "ssh deploy@localhost -p 2222"
}