local reserved = redis.call('HGET', KEYS[2], ARGV[1])
if (not reserved) then
    return 0
end

reserved = tonumber(reserved)
redis.call('INCRBY', KEYS[1], reserved)
redis.call('HDEL', KEYS[2], ARGV[1])

if (redis.call('HLEN', KEYS[2]) == 0) then
    redis.call('DEL', KEYS[2])
end

return reserved
