package com.callcenter.app.data.repository

import com.callcenter.app.data.local.dao.ContactDao
import com.callcenter.app.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通讯录联系人 Repository
 */
@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao
) {

    fun getAllContacts(): Flow<List<ContactEntity>> = contactDao.getAllContacts()

    fun searchContacts(query: String): Flow<List<ContactEntity>> = contactDao.searchContacts(query)

    suspend fun getContactById(id: Int): ContactEntity? = contactDao.getContactById(id)

    suspend fun addContact(name: String, phone: String, email: String? = null, company: String? = null, notes: String? = null): Long {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val contact = ContactEntity(
            name = name,
            phone = phone,
            email = email,
            company = company,
            notes = notes,
            createdAt = currentTime,
            updatedAt = currentTime
        )
        return contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: ContactEntity) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        contactDao.updateContact(contact.copy(updatedAt = currentTime))
    }

    suspend fun deleteContact(contact: ContactEntity) {
        contactDao.deleteContact(contact)
    }

    suspend fun deleteContactById(id: Int) {
        contactDao.deleteContactById(id)
    }

    fun getContactCount(): Flow<Int> = contactDao.getContactCount()
}
