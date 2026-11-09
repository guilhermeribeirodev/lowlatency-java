output "public_ip" {
  value = azurerm_public_ip.pip.ip_address
}

output "ssh_command" {
  value = "ssh ${var.admin_username}@${azurerm_public_ip.pip.ip_address}"
}

output "deploy_command" {
  value = "../scripts/deploy-azure.sh ${var.admin_username}@${azurerm_public_ip.pip.ip_address}"
}

output "resource_group" {
  value = azurerm_resource_group.rg.name
}
