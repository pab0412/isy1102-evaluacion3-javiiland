import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { extractErrorMessage } from '../api/axiosClient'
import { reservaService } from '../api/reservaService'

export default function ConfirmarAsistencia() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const requestRef = useRef({ token: null, promise: null })
  const [loading, setLoading] = useState(true)
  const [success, setSuccess] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    if (!token) {
      setError('No se encontró el token de confirmación en el enlace.')
      setLoading(false)
      return
    }

    setLoading(true)
    setSuccess('')
    setError('')

    if (requestRef.current.token !== token) {
      requestRef.current = {
        token,
        promise: reservaService.confirmarAsistencia(token),
      }
    }

    requestRef.current.promise
      .then((message) => {
        if (active) setSuccess(message || '¡Asistencia confirmada!')
      })
      .catch((err) => {
        if (active) setError(extractErrorMessage(err))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [token])

  return (
    <div className="page page--narrow page--center">
      <h1>Confirmación de asistencia</h1>
      {loading && <p className="muted">Validando tu asistencia...</p>}
      {!loading && success && <p className="form-success">{success}</p>}
      {!loading && error && <p className="form-error">{error}</p>}
    </div>
  )
}