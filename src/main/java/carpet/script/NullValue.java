package carpet.script;

public class NullValue extends Value
{
    @Override
    public String getString()
    {
        return "";
    }

    @Override
    public boolean getBoolean()
    {
        return false;
    }

    @Override
    public Value clone()
    {
        return new NullValue();
    }
    public NullValue()
    {}
}
