variable "subscription_id" {
  description = "Azure subscription ID (azurerm 4.x requires it explicitly)."
  type        = string
}

variable "name" {
  description = "Prefix for all resource names."
  type        = string
  default     = "lowlat"
}

variable "location" {
  description = "Azure region. Pick the one closest to your counterparties/exchange/clients."
  type        = string
  default     = "uksouth"
}

variable "vm_size" {
  description = <<-EOT
    VM size. Needs accelerated networking support. Start with a general-purpose size and compare
    with compute-optimised/high-clock sizes using the JMH + Main numbers. Check regional
    availability: az vm list-skus --location <region> --size <prefix> --output table
  EOT
  type        = string
  default     = "Standard_D8s_v5"
}

variable "admin_username" {
  type    = string
  default = "azureuser"
}

variable "ssh_public_key_path" {
  description = "Path to the SSH public key used for the admin user."
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "ssh_allowed_cidr" {
  description = "CIDR allowed to SSH in (e.g. your office/home IP as x.x.x.x/32). Never use 0.0.0.0/0."
  type        = string
}

variable "app_allowed_cidr" {
  description = "CIDR allowed to reach the application ports (TCP/UDP)."
  type        = string
}

variable "app_port" {
  description = "Port for the app's TCP/UDP endpoints (used by NSG rules; wire it into your service)."
  type        = number
  default     = 9000
}

variable "jvm_heap" {
  description = "Fixed heap size (-Xms = -Xmx)."
  type        = string
  default     = "4g"
}

variable "isolated_cpus" {
  description = <<-EOT
    Optional CPU list to isolate from the kernel scheduler and pin the app to, e.g. "2-3".
    Empty string disables isolation. Setting it adds isolcpus/nohz_full/rcu_nocbs kernel args and
    triggers one reboot during first boot.
  EOT
  type        = string
  default     = ""
}

variable "use_proximity_placement_group" {
  description = "Create a proximity placement group. Useful once you add more VMs that must be close together."
  type        = bool
  default     = true
}

variable "tags" {
  type    = map(string)
  default = { project = "lowlatency-java" }
}
