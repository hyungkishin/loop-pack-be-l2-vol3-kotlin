package com.loopers.interfaces.api.auth

import com.loopers.domain.user.BirthDate
import com.loopers.domain.user.Email
import com.loopers.domain.user.GenderType
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.Name
import com.loopers.domain.user.Password
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AuthenticationFilterTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var filter: AuthenticationFilter
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        passwordEncoder = mockk()
        filter = AuthenticationFilter(userRepository, passwordEncoder)
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        filterChain = mockk(relaxed = true)
    }

    @Test
    fun `인증 헤더가 없으면 AuthUser를 설정하지 않고 통과한다`() {
        filter.doFilter(request, response, filterChain)

        assertThat(request.getAttribute(AuthenticationFilter.AUTH_USER_ATTRIBUTE)).isNull()
        verify { filterChain.doFilter(request, response) }
    }

    @Test
    fun `인증 헤더가 있고 유효하면 AuthUser를 설정한다`() {
        val user = createUser()

        request.addHeader(AuthenticationFilter.HEADER_LOGIN_ID, LOGIN_ID)
        request.addHeader(AuthenticationFilter.HEADER_LOGIN_PW, PASSWORD)

        every { userRepository.findByLoginId(LoginId(LOGIN_ID)) } returns user
        every { passwordEncoder.matches(PASSWORD, any()) } returns true

        filter.doFilter(request, response, filterChain)

        val authUser = request.getAttribute(AuthenticationFilter.AUTH_USER_ATTRIBUTE) as AuthUser
        assertThat(authUser.id).isEqualTo(USER_ID)
        assertThat(authUser.loginId).isEqualTo(LOGIN_ID)
        verify { filterChain.doFilter(request, response) }
    }

    @Test
    fun `사용자가 존재하지 않으면 AuthUser를 설정하지 않는다`() {
        request.addHeader(AuthenticationFilter.HEADER_LOGIN_ID, "nouser")
        request.addHeader(AuthenticationFilter.HEADER_LOGIN_PW, PASSWORD)

        every { userRepository.findByLoginId(LoginId("nouser")) } returns null

        filter.doFilter(request, response, filterChain)

        assertThat(request.getAttribute(AuthenticationFilter.AUTH_USER_ATTRIBUTE)).isNull()
        verify { filterChain.doFilter(request, response) }
    }

    @Test
    fun `비밀번호가 틀리면 AuthUser를 설정하지 않는다`() {
        val user = createUser()
        request.addHeader(AuthenticationFilter.HEADER_LOGIN_ID, LOGIN_ID)
        request.addHeader(AuthenticationFilter.HEADER_LOGIN_PW, WRONG_PASSWORD)

        every { userRepository.findByLoginId(LoginId(LOGIN_ID)) } returns user
        every { passwordEncoder.matches(WRONG_PASSWORD, any()) } returns false

        filter.doFilter(request, response, filterChain)

        assertThat(request.getAttribute(AuthenticationFilter.AUTH_USER_ATTRIBUTE)).isNull()
        assertThat(request.getAttribute(AuthenticationFilter.AUTH_FAILED_ATTRIBUTE)).isEqualTo(true)

        verify { filterChain.doFilter(request, response) }
    }

    private fun createUser(): User {
        return User.reconstitute(
            persistenceId = USER_ID,
            loginId = LoginId(LOGIN_ID),
            password = Password.fromEncoded("encoded"),
            name = Name(NAME),
            birthDate = BirthDate.from(BIRTH_DATE),
            email = Email(EMAIL),
            gender = GenderType.MALE,
        )
    }

    companion object {
        private const val USER_ID = 1L
        private const val LOGIN_ID = "tkaqkeldk"
        private const val PASSWORD = "Password1!"
        private const val WRONG_PASSWORD = "WrongPass1!"
        private const val NAME = "신형기"
        private const val BIRTH_DATE = "1993-04-01"
        private const val EMAIL = "test@example.com"
    }
}
