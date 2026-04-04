import { useEffect } from "react";
import { type ContainerHookProps } from "components/system/Apps/AppContainer";

const useTapID = ({ setLoading }: ContainerHookProps): void => {
  useEffect(() => {
    setLoading(false);
  }, [setLoading]);
};

export default useTapID;
